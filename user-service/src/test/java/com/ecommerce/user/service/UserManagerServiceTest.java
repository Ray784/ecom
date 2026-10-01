package com.ecommerce.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.AddressDTO;
import com.ecommerce.common.dto.ProfileUpdateDTO;
import com.ecommerce.common.dto.UserProfileDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.user.client.IamClient;
import com.ecommerce.user.client.IamUserAccount;
import com.ecommerce.user.model.Address;
import com.ecommerce.user.model.UserProfile;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class UserManagerServiceTest {

  private UserProfileRepository profileRepository;
  private AddressRepository addressRepository;
  private IamClient iamClient;
  private UserManagerService service;

  @BeforeEach
  void setUp() {
    profileRepository = mock(UserProfileRepository.class);
    addressRepository = mock(AddressRepository.class);
    iamClient = mock(IamClient.class);
    service = new UserManagerService(profileRepository, addressRepository, iamClient, 2);
    when(profileRepository.saveAndFlush(any(UserProfile.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(addressRepository.saveAndFlush(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
    when(addressRepository.findByUserIdOrderByIdAsc(any())).thenReturn(List.of());
    when(profileRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo(code);
  }

  private UserProfile existingProfile(String userId) {
    UserProfile profile = new UserProfile(userId, "Ann", "Lee", "ann@example.com");
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    return profile;
  }

  private static Address address(Long id, String userId, boolean isDefault) {
    Address address = new Address(userId, "1 Main St", "Springfield", "12345", isDefault);
    ReflectionTestUtils.setField(address, "id", id);
    return address;
  }

  @Test
  void verifyAccess_allowsInternalOwnerAndAdmin() {
    assertThatCode(() -> service.verifyAccess("u-1", null, null)).doesNotThrowAnyException();
    assertThatCode(() -> service.verifyAccess("u-1", "u-1", "ROLE_CUSTOMER"))
        .doesNotThrowAnyException();
    assertThatCode(() -> service.verifyAccess("u-1", "u-2", "ROLE_CUSTOMER,ROLE_ADMIN"))
        .doesNotThrowAnyException();
  }

  @Test
  void verifyAccess_otherNonAdminCaller_throwsUser4007() {
    assertCode(() -> service.verifyAccess("u-1", "u-2", "ROLE_CUSTOMER"), "USER_4007");
    assertCode(() -> service.verifyAccess("u-1", "u-2", null), "USER_4007");
  }

  @Test
  void fetchUserProfile_existing_includesAddresses() {
    existingProfile("u-1");
    when(addressRepository.findByUserIdOrderByIdAsc("u-1"))
        .thenReturn(List.of(address(7L, "u-1", true)));

    UserProfileDTO dto = service.fetchUserProfile("u-1");

    assertThat(dto.getFirstName()).isEqualTo("Ann");
    assertThat(dto.getAddresses()).extracting(AddressDTO::getId).containsExactly(7L);
  }

  @Test
  void fetchUserProfile_missingButKnownToIam_isAutoInitialized() {
    when(profileRepository.findById("u-1")).thenReturn(Optional.empty());
    when(iamClient.fetchAccount("u-1"))
        .thenReturn(Optional.of(new IamUserAccount("u-1", "ann", "ann@example.com", "Ann", true)));

    UserProfileDTO dto = service.fetchUserProfile("u-1");

    assertThat(dto.getEmail()).isEqualTo("ann@example.com");
    assertThat(dto.getFirstName()).isEqualTo("Ann");
    verify(profileRepository).saveAndFlush(any(UserProfile.class));
  }

  @Test
  void fetchUserProfile_autoInitWithTakenEmail_dropsEmail() {
    when(profileRepository.findById("u-1")).thenReturn(Optional.empty());
    when(profileRepository.findByEmailIgnoreCase("ann@example.com"))
        .thenReturn(Optional.of(new UserProfile("u-9", null, null, "ann@example.com")));
    when(iamClient.fetchAccount("u-1"))
        .thenReturn(Optional.of(new IamUserAccount("u-1", "ann", "ann@example.com", "Ann", true)));

    assertThat(service.fetchUserProfile("u-1").getEmail()).isNull();
  }

  @Test
  void fetchUserProfile_unknownEverywhere_throwsUser4001() {
    when(profileRepository.findById("u-404")).thenReturn(Optional.empty());
    when(iamClient.fetchAccount("u-404")).thenReturn(Optional.empty());
    assertCode(() -> service.fetchUserProfile("u-404"), "USER_4001");
  }

  @Test
  void fetchUserProfile_iamDown_propagatesUser5002() {
    when(profileRepository.findById("u-1")).thenReturn(Optional.empty());
    when(iamClient.fetchAccount("u-1"))
        .thenThrow(CodedErrorFactory.create(MappedError.USER_5002));
    assertCode(() -> service.fetchUserProfile("u-1"), "USER_5002");
  }

  @Test
  void createOrUpdateProfile_newProfile_isCreated() {
    when(profileRepository.findById("u-1")).thenReturn(Optional.empty());

    UserProfileDTO dto =
        service.createOrUpdateProfile("u-1", new ProfileUpdateDTO("Ann", "Lee", "ann@example.com"));

    assertThat(dto.getUserId()).isEqualTo("u-1");
    assertThat(dto.getLastName()).isEqualTo("Lee");
  }

  @Test
  void createOrUpdateProfile_nullFieldsKeepExistingValues() {
    existingProfile("u-1");

    UserProfileDTO dto = service.createOrUpdateProfile("u-1", new ProfileUpdateDTO(null, "Kim", null));

    assertThat(dto.getFirstName()).isEqualTo("Ann");
    assertThat(dto.getLastName()).isEqualTo("Kim");
    assertThat(dto.getEmail()).isEqualTo("ann@example.com");
  }

  @Test
  void createOrUpdateProfile_nullBody_justEnsuresProfileExists() {
    when(profileRepository.findById("u-1")).thenReturn(Optional.empty());
    assertThat(service.createOrUpdateProfile("u-1", null).getUserId()).isEqualTo("u-1");
  }

  @Test
  void updateProfile_invalidEmail_throwsUser4004() {
    existingProfile("u-1");
    assertCode(
        () -> service.updateProfile("u-1", new ProfileUpdateDTO(null, null, "not-an-email")),
        "USER_4004");
  }

  @Test
  void updateProfile_emailOwnedByOtherUser_throwsUser4002() {
    existingProfile("u-1");
    when(profileRepository.findByEmailIgnoreCase("bob@example.com"))
        .thenReturn(Optional.of(new UserProfile("u-2", null, null, "bob@example.com")));
    assertCode(
        () -> service.updateProfile("u-1", new ProfileUpdateDTO(null, null, "bob@example.com")),
        "USER_4002");
  }

  @Test
  void updateProfile_ownEmailAgain_isAllowed() {
    UserProfile profile = existingProfile("u-1");
    when(profileRepository.findByEmailIgnoreCase("ann@example.com")).thenReturn(Optional.of(profile));

    assertThatCode(
            () -> service.updateProfile("u-1", new ProfileUpdateDTO(null, null, "ann@example.com")))
        .doesNotThrowAnyException();
  }

  @Test
  void updateProfile_missingProfile_throwsUser4001() {
    when(profileRepository.findById("u-404")).thenReturn(Optional.empty());
    assertCode(() -> service.updateProfile("u-404", new ProfileUpdateDTO()), "USER_4001");
  }

  @Test
  void updateProfile_saveFails_throwsUser5001() {
    existingProfile("u-1");
    when(profileRepository.saveAndFlush(any(UserProfile.class)))
        .thenThrow(new DataAccessResourceFailureException("db down"));
    assertCode(() -> service.updateProfile("u-1", new ProfileUpdateDTO("X", null, null)), "USER_5001");
  }

  @Test
  void addAddress_firstAddress_becomesDefault() {
    existingProfile("u-1");

    AddressDTO dto = service.addAddress("u-1", new AddressDTO(null, "1 Main", "Town", "12345", false));

    assertThat(dto.isDefault()).isTrue();
  }

  @Test
  void addAddress_newDefault_clearsPreviousDefault() {
    existingProfile("u-1");
    Address previous = address(1L, "u-1", true);
    when(addressRepository.findByUserIdOrderByIdAsc("u-1")).thenReturn(List.of(previous));

    AddressDTO dto = service.addAddress("u-1", new AddressDTO(null, "2 Side", "Town", "560001", true));

    assertThat(dto.isDefault()).isTrue();
    assertThat(previous.isDefault()).isFalse();
    verify(addressRepository).save(previous);
  }

  @Test
  void addAddress_nonDefaultSecondAddress_keepsExistingDefault() {
    existingProfile("u-1");
    Address previous = address(1L, "u-1", true);
    when(addressRepository.findByUserIdOrderByIdAsc("u-1")).thenReturn(List.of(previous));

    AddressDTO dto =
        service.addAddress("u-1", new AddressDTO(null, "2 Side", "Town", "12345-6789", false));

    assertThat(dto.isDefault()).isFalse();
    assertThat(previous.isDefault()).isTrue();
  }

  @Test
  void addAddress_blankFields_throwUser4005() {
    existingProfile("u-1");
    assertCode(() -> service.addAddress("u-1", null), "USER_4005");
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, " ", "Town", "12345", false)), "USER_4005");
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, "1 Main", null, "12345", false)),
        "USER_4005");
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, "1 Main", "Town", "", false)), "USER_4005");
  }

  @Test
  void addAddress_badZip_throwsUser4009() {
    existingProfile("u-1");
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, "1 Main", "Town", "ABC", false)),
        "USER_4009");
  }

  @Test
  void addAddress_limitReached_throwsUser4003() {
    existingProfile("u-1");
    when(addressRepository.findByUserIdOrderByIdAsc("u-1"))
        .thenReturn(List.of(address(1L, "u-1", true), address(2L, "u-1", false)));
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, "1 Main", "Town", "12345", false)),
        "USER_4003");
  }

  @Test
  void addAddress_unknownProfile_throwsUser4001() {
    when(profileRepository.findById("u-404")).thenReturn(Optional.empty());
    when(iamClient.fetchAccount("u-404")).thenReturn(Optional.empty());
    assertCode(
        () -> service.addAddress("u-404", new AddressDTO(null, "1 Main", "Town", "12345", false)),
        "USER_4001");
  }

  @Test
  void addAddress_saveFails_throwsUser5001() {
    existingProfile("u-1");
    when(addressRepository.saveAndFlush(any(Address.class)))
        .thenThrow(new DataAccessResourceFailureException("db down"));
    assertCode(
        () -> service.addAddress("u-1", new AddressDTO(null, "1 Main", "Town", "12345", false)),
        "USER_5001");
  }

  @Test
  void deleteAddress_nonDefault_isDeleted() {
    Address target = address(2L, "u-1", false);
    when(addressRepository.findById(2L)).thenReturn(Optional.of(target));

    service.deleteAddress("u-1", 2L);

    verify(addressRepository).delete(target);
  }

  @Test
  void deleteAddress_onlyDefaultAddress_isDeleted() {
    Address target = address(1L, "u-1", true);
    when(addressRepository.findById(1L)).thenReturn(Optional.of(target));
    when(addressRepository.countByUserId("u-1")).thenReturn(1L);

    service.deleteAddress("u-1", 1L);

    verify(addressRepository).delete(target);
  }

  @Test
  void deleteAddress_defaultWithOthers_throwsUser4006() {
    when(addressRepository.findById(1L)).thenReturn(Optional.of(address(1L, "u-1", true)));
    when(addressRepository.countByUserId("u-1")).thenReturn(2L);

    assertCode(() -> service.deleteAddress("u-1", 1L), "USER_4006");
    verify(addressRepository, never()).delete(any(Address.class));
  }

  @Test
  void deleteAddress_missingOrForeignOrNull_throwsUser4008() {
    when(addressRepository.findById(5L)).thenReturn(Optional.empty());
    when(addressRepository.findById(6L)).thenReturn(Optional.of(address(6L, "u-2", false)));

    assertCode(() -> service.deleteAddress("u-1", 5L), "USER_4008");
    assertCode(() -> service.deleteAddress("u-1", 6L), "USER_4008");
    assertCode(() -> service.deleteAddress("u-1", null), "USER_4008");
  }
}
