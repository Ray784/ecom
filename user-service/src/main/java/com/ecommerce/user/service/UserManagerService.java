package com.ecommerce.user.service;

import com.ecommerce.common.dto.AddressDTO;
import com.ecommerce.common.dto.ProfileUpdateDTO;
import com.ecommerce.common.dto.UserProfileDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.user.client.IamClient;
import com.ecommerce.user.client.IamUserAccount;
import com.ecommerce.user.model.Address;
import com.ecommerce.user.model.UserProfile;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.UserProfileRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile and address management for platform users. */
@Service
public class UserManagerService {

  static final String ADMIN_ROLE = "ROLE_ADMIN";
  static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /** Five digit US style codes with optional plus four extension, or six digit PIN codes. */
  static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$|^\\d{6}$");

  private final UserProfileRepository profileRepository;
  private final AddressRepository addressRepository;
  private final IamClient iamClient;
  private final int maxAddresses;

  public UserManagerService(
      UserProfileRepository profileRepository,
      AddressRepository addressRepository,
      IamClient iamClient,
      @Value("${user.address.max:5}") int maxAddresses) {
    this.profileRepository = profileRepository;
    this.addressRepository = addressRepository;
    this.iamClient = iamClient;
    this.maxAddresses = maxAddresses;
  }

  /**
   * Rejects a gateway authenticated caller acting on someone else's profile with USER_4007.
   *
   * <p>Requests without an X-User-Id header are internal and trusted, and ROLE_ADMIN callers may
   * access any profile.
   */
  public void verifyAccess(String profileId, String callerId, String callerRoles) {
    if (callerId == null || callerId.equals(profileId)) {
      return;
    }
    boolean isAdmin =
        callerRoles != null
            && Arrays.stream(callerRoles.split(",")).map(String::trim).anyMatch(ADMIN_ROLE::equals);
    if (!isAdmin) {
      throw CodedErrorFactory.create(MappedError.USER_4007, profileId);
    }
  }

  /**
   * Returns the profile with its addresses.
   *
   * <p>A missing profile is auto initialized from the IAM account when one exists, otherwise
   * USER_4001 is thrown.
   */
  @Transactional
  public UserProfileDTO fetchUserProfile(String userId) {
    return toDto(requireProfile(userId));
  }

  /** Creates the profile if it does not exist yet, then applies every non null field. */
  @Transactional
  public UserProfileDTO createOrUpdateProfile(String userId, ProfileUpdateDTO update) {
    UserProfile profile =
        profileRepository.findById(userId).orElseGet(() -> new UserProfile(userId, null, null, null));
    return toDto(applyAndSave(profile, update));
  }

  /** Applies every non null field to an existing profile, throwing USER_4001 if it is missing. */
  @Transactional
  public UserProfileDTO updateProfile(String userId, ProfileUpdateDTO update) {
    UserProfile profile =
        profileRepository
            .findById(userId)
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.USER_4001, userId));
    return toDto(applyAndSave(profile, update));
  }

  /**
   * Adds an address to the profile.
   *
   * <p>Throws USER_4005 for a blank field, USER_4009 for a malformed zip code and USER_4003 when
   * the address limit is reached. The first address, or one flagged isDefault, becomes the only
   * default address.
   */
  @Transactional
  public AddressDTO addAddress(String userId, AddressDTO request) {
    requireProfile(userId);
    if (request == null) {
      throw CodedErrorFactory.create(MappedError.USER_4005, "street");
    }
    String street = requireAddressField(request.getStreet(), "street");
    String city = requireAddressField(request.getCity(), "city");
    String zipCode = requireAddressField(request.getZipCode(), "zipCode");
    if (!ZIP_PATTERN.matcher(zipCode).matches()) {
      throw CodedErrorFactory.create(MappedError.USER_4009, zipCode);
    }
    List<Address> existing = addressRepository.findByUserIdOrderByIdAsc(userId);
    if (existing.size() >= maxAddresses) {
      throw CodedErrorFactory.create(MappedError.USER_4003, userId, maxAddresses);
    }
    boolean makeDefault = existing.isEmpty() || request.isDefault();
    try {
      if (makeDefault) {
        for (Address other : existing) {
          if (other.isDefault()) {
            other.setDefault(false);
            addressRepository.save(other);
          }
        }
      }
      Address saved =
          addressRepository.saveAndFlush(new Address(userId, street, city, zipCode, makeDefault));
      return toDto(saved);
    } catch (DataAccessException e) {
      throw CodedErrorFactory.create(MappedError.USER_5001, e, userId);
    }
  }

  /**
   * Deletes an address owned by the user.
   *
   * <p>Throws USER_4008 if the address does not exist or belongs to another user, and USER_4006
   * when deleting the default address while other addresses remain.
   */
  @Transactional
  public void deleteAddress(String userId, Long addressId) {
    Address address =
        Optional.ofNullable(addressId)
            .flatMap(addressRepository::findById)
            .filter(a -> a.getUserId().equals(userId))
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.USER_4008, addressId, userId));
    if (address.isDefault() && addressRepository.countByUserId(userId) > 1) {
      throw CodedErrorFactory.create(MappedError.USER_4006, addressId);
    }
    addressRepository.delete(address);
  }

  private UserProfile requireProfile(String userId) {
    return profileRepository
        .findById(userId)
        .orElseGet(
            () ->
                iamClient
                    .fetchAccount(userId)
                    .map(this::initializeFromIam)
                    .orElseThrow(() -> CodedErrorFactory.create(MappedError.USER_4001, userId)));
  }

  private UserProfile initializeFromIam(IamUserAccount account) {
    String email = account.email();
    if (email != null && profileRepository.findByEmailIgnoreCase(email).isPresent()) {
      email = null;
    }
    return save(new UserProfile(account.userId(), account.firstName(), null, email));
  }

  private UserProfile applyAndSave(UserProfile profile, ProfileUpdateDTO update) {
    if (update != null) {
      if (update.getEmail() != null) {
        String email = update.getEmail().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
          throw CodedErrorFactory.create(MappedError.USER_4004, email);
        }
        profileRepository
            .findByEmailIgnoreCase(email)
            .filter(other -> !other.getUserId().equals(profile.getUserId()))
            .ifPresent(
                other -> {
                  throw CodedErrorFactory.create(MappedError.USER_4002, email);
                });
        profile.setEmail(email);
      }
      if (update.getFirstName() != null) {
        profile.setFirstName(update.getFirstName());
      }
      if (update.getLastName() != null) {
        profile.setLastName(update.getLastName());
      }
    }
    return save(profile);
  }

  private UserProfile save(UserProfile profile) {
    try {
      return profileRepository.saveAndFlush(profile);
    } catch (DataAccessException e) {
      throw CodedErrorFactory.create(MappedError.USER_5001, e, profile.getUserId());
    }
  }

  private UserProfileDTO toDto(UserProfile profile) {
    List<AddressDTO> addresses =
        addressRepository.findByUserIdOrderByIdAsc(profile.getUserId()).stream()
            .map(UserManagerService::toDto)
            .toList();
    return new UserProfileDTO(
        profile.getUserId(),
        profile.getFirstName(),
        profile.getLastName(),
        profile.getEmail(),
        addresses);
  }

  private static AddressDTO toDto(Address address) {
    return new AddressDTO(
        address.getId(),
        address.getStreet(),
        address.getCity(),
        address.getZipCode(),
        address.isDefault());
  }

  private static String requireAddressField(String value, String field) {
    if (value == null || value.isBlank()) {
      throw CodedErrorFactory.create(MappedError.USER_4005, field);
    }
    return value.trim();
  }
}
