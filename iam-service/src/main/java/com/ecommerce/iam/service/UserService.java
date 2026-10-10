package com.ecommerce.iam.service;

import com.ecommerce.common.dto.BaseResponseDTO;
import com.ecommerce.common.dto.RegistrationDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.iam.dto.UserAccountDTO;
import com.ecommerce.iam.model.JWTToken;
import com.ecommerce.iam.model.Role;
import com.ecommerce.iam.model.User;
import com.ecommerce.iam.repository.JWTTokenRepository;
import com.ecommerce.iam.repository.RoleRepository;
import com.ecommerce.iam.repository.UserRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account registration and credential management. */
@Service
public class UserService {

  /** Allowed characters for user IDs: letters, digits, dash and underscore, 1 to 64 long. */
  static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

  /** Minimum password length enforced together with upper, lower and digit requirements. */
  static final int MIN_PASSWORD_LENGTH = 8;

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final JWTTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      JWTTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Registers a new account.
   *
   * <p>Throws IAM_4006 for a missing username, password or email, IAM_4004 for a taken username
   * and IAM_4005 for a weak password. Usernames starting with admin or an explicit ROLE_ADMIN
   * role get ROLE_ADMIN plus ROLE_CUSTOMER, ROLE_SERVICE is honoured, anything else becomes
   * ROLE_CUSTOMER.
   */
  @Transactional
  public UserDetailsDTO registerNewUser(RegistrationDTO details) {
    if (details == null) {
      throw CodedErrorFactory.create(MappedError.IAM_4006, "username");
    }
    String username = requireField(details.getUsername(), "username").trim();
    String password = requireField(details.getPassword(), "password");
    String email = requireField(details.getEmail(), "email").trim();
    if (userRepository.existsByUsername(username)) {
      throw CodedErrorFactory.create(MappedError.IAM_4004, username);
    }
    requireStrongPassword(password);

    User user =
        new User(
            UUID.randomUUID().toString(),
            username,
            email,
            details.getFirstName(),
            passwordEncoder.encode(password));
    user.setRoles(resolveRoles(username, details.getRole()));
    User saved = userRepository.save(user);

    UserDetailsDTO response =
        new UserDetailsDTO(
            saved.getUserId(), saved.getUsername(), saved.getRoleNames(), saved.getEmail(), true);
    response.setStatusCode(201);
    response.setMessage("User registered successfully");
    return response;
  }

  /**
   * Replaces the password of an account and revokes every token issued to it.
   *
   * <p>Throws IAM_4009 for a malformed user ID, USER_4001 for an unknown user and IAM_4005 for a
   * weak password.
   */
  @Transactional
  public void updatePassword(String userId, String newPassword) {
    User user = requireUser(userId);
    if (newPassword == null) {
      throw CodedErrorFactory.create(MappedError.IAM_4005);
    }
    requireStrongPassword(newPassword);
    user.setHashedPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    revokeTokens(user.getUserId());
  }

  /** Disables an account and revokes its tokens, after which login fails with IAM_4003. */
  @Transactional
  public BaseResponseDTO lockUser(String userId) {
    User user = requireUser(userId);
    user.setEnabled(false);
    userRepository.save(user);
    revokeTokens(user.getUserId());
    return BaseResponseDTO.ok("User " + user.getUsername() + " locked");
  }

  /** Returns the account for internal callers such as user-service profile initialization. */
  @Transactional(readOnly = true)
  public UserAccountDTO getUserAccount(String userId) {
    User user = requireUser(userId);
    return new UserAccountDTO(
        user.getUserId(),
        user.getUsername(),
        user.getRoleNames(),
        user.getEmail(),
        user.getFirstName(),
        user.isEnabled());
  }

  /**
   * Enforces that a gateway authenticated caller holds ROLE_ADMIN.
   *
   * <p>The check only applies when the gateway forwarded an X-User-Roles header; direct internal
   * calls without the header are trusted. Throws IAM_4008 otherwise.
   */
  public void requireAdminIfForwarded(String callerId, String rolesHeader) {
    if (rolesHeader == null) {
      return;
    }
    boolean isAdmin =
        Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .anyMatch(Role.ADMIN::equals);
    if (!isAdmin) {
      throw CodedErrorFactory.create(
          MappedError.IAM_4008, callerId == null ? "anonymous" : callerId, Role.ADMIN);
    }
  }

  /**
   * Enforces that a gateway authenticated caller acts on its own account unless it holds
   * ROLE_ADMIN.
   *
   * <p>The check only applies when the gateway forwarded a non blank X-User-Id header; direct
   * internal calls without the header are trusted. Throws IAM_4008 when the caller targets another
   * account without ROLE_ADMIN.
   */
  public void requireSelfOrAdmin(String targetUserId, String callerId, String rolesHeader) {
    if (callerId == null || callerId.isBlank() || callerId.equals(targetUserId)) {
      return;
    }
    if (!hasAdminRole(rolesHeader)) {
      throw CodedErrorFactory.create(MappedError.IAM_4008, callerId, Role.ADMIN);
    }
  }

  private static boolean hasAdminRole(String rolesHeader) {
    return rolesHeader != null
        && Arrays.stream(rolesHeader.split(",")).map(String::trim).anyMatch(Role.ADMIN::equals);
  }

  Set<Role> resolveRoles(String username, String requestedRole) {
    String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase(Locale.ROOT);
    Set<Role> roles = new LinkedHashSet<>();
    if (Role.ADMIN.equals(role) || username.toLowerCase(Locale.ROOT).startsWith("admin")) {
      roles.add(findRole(Role.ADMIN));
      roles.add(findRole(Role.CUSTOMER));
    } else if (Role.SERVICE.equals(role)) {
      roles.add(findRole(Role.SERVICE));
    } else {
      roles.add(findRole(Role.CUSTOMER));
    }
    return roles;
  }

  static boolean isStrongPassword(String password) {
    return password.length() >= MIN_PASSWORD_LENGTH
        && password.chars().anyMatch(Character::isUpperCase)
        && password.chars().anyMatch(Character::isLowerCase)
        && password.chars().anyMatch(Character::isDigit);
  }

  private Role findRole(String name) {
    return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name)));
  }

  private User requireUser(String userId) {
    if (userId == null || !USER_ID_PATTERN.matcher(userId).matches()) {
      throw CodedErrorFactory.create(MappedError.IAM_4009, userId);
    }
    return userRepository
        .findByUserId(userId)
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.USER_4001, userId));
  }

  private void revokeTokens(String userId) {
    for (JWTToken token : tokenRepository.findByUserIdAndBlacklistedFalse(userId)) {
      token.setBlacklisted(true);
      tokenRepository.save(token);
    }
  }

  private static void requireStrongPassword(String password) {
    if (!isStrongPassword(password)) {
      throw CodedErrorFactory.create(MappedError.IAM_4005);
    }
  }

  private static String requireField(String value, String field) {
    if (value == null || value.isBlank()) {
      throw CodedErrorFactory.create(MappedError.IAM_4006, field);
    }
    return value;
  }
}
