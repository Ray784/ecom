package com.ecommerce.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.RegistrationDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.iam.dto.UserAccountDTO;
import com.ecommerce.iam.model.JWTToken;
import com.ecommerce.iam.model.Role;
import com.ecommerce.iam.model.User;
import com.ecommerce.iam.repository.JWTTokenRepository;
import com.ecommerce.iam.repository.RoleRepository;
import com.ecommerce.iam.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private UserRepository userRepository;
  private RoleRepository roleRepository;
  private JWTTokenRepository tokenRepository;
  private UserService userService;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    roleRepository = mock(RoleRepository.class);
    tokenRepository = mock(JWTTokenRepository.class);
    userService = new UserService(userRepository, roleRepository, tokenRepository, encoder);
    for (String name : List.of(Role.CUSTOMER, Role.ADMIN, Role.SERVICE)) {
      when(roleRepository.findByName(name)).thenReturn(Optional.of(new Role(name)));
    }
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo(code);
  }

  private static RegistrationDTO registration(String username, String role) {
    return new RegistrationDTO(username, "Secret123", username + "@example.com", "First", role);
  }

  @Test
  void registerNewUser_defaultsToCustomerRoleAndHashesPassword() {
    UserDetailsDTO result = userService.registerNewUser(registration("bob", null));

    assertThat(result.getRoles()).containsExactly(Role.CUSTOMER);
    assertThat(result.getStatusCode()).isEqualTo(201);
    assertThat(result.getUserId()).matches(UserService.USER_ID_PATTERN.pattern());
    verify(userRepository).save(any(User.class));
  }

  @Test
  void registerNewUser_adminUsernamePrefix_getsAdminAndCustomer() {
    UserDetailsDTO result = userService.registerNewUser(registration("AdminJane", null));
    assertThat(result.getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CUSTOMER);
  }

  @Test
  void registerNewUser_explicitAdminRole_getsAdminAndCustomer() {
    UserDetailsDTO result = userService.registerNewUser(registration("jane", "role_admin"));
    assertThat(result.getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CUSTOMER);
  }

  @Test
  void registerNewUser_serviceRole_getsServiceOnly() {
    UserDetailsDTO result = userService.registerNewUser(registration("svc", Role.SERVICE));
    assertThat(result.getRoles()).containsExactly(Role.SERVICE);
  }

  @Test
  void registerNewUser_missingRole_isCreatedOnTheFly() {
    when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    UserDetailsDTO result = userService.registerNewUser(registration("bob", "UNKNOWN"));

    assertThat(result.getRoles()).containsExactly(Role.CUSTOMER);
  }

  @Test
  void registerNewUser_missingFields_throwIam4006() {
    assertCode(() -> userService.registerNewUser(null), "IAM_4006");
    assertCode(
        () -> userService.registerNewUser(new RegistrationDTO(" ", "Secret123", "e@x.io", null, null)),
        "IAM_4006");
    assertCode(
        () -> userService.registerNewUser(new RegistrationDTO("bob", null, "e@x.io", null, null)),
        "IAM_4006");
    assertCode(
        () -> userService.registerNewUser(new RegistrationDTO("bob", "Secret123", "", null, null)),
        "IAM_4006");
  }

  @Test
  void registerNewUser_takenUsername_throwsIam4004() {
    when(userRepository.existsByUsername("bob")).thenReturn(true);
    assertCode(() -> userService.registerNewUser(registration("bob", null)), "IAM_4004");
  }

  @Test
  void registerNewUser_weakPassword_throwsIam4005() {
    RegistrationDTO weak = new RegistrationDTO("bob", "password", "b@x.io", null, null);
    assertCode(() -> userService.registerNewUser(weak), "IAM_4005");
  }

  @Test
  void isStrongPassword_requiresLengthUpperLowerDigit() {
    assertThat(UserService.isStrongPassword("Secret123")).isTrue();
    assertThat(UserService.isStrongPassword("Sec12")).isFalse();
    assertThat(UserService.isStrongPassword("secret123")).isFalse();
    assertThat(UserService.isStrongPassword("SECRET123")).isFalse();
    assertThat(UserService.isStrongPassword("SecretABC")).isFalse();
  }

  @Test
  void updatePassword_success_rehashesAndRevokesTokens() {
    User user = new User("u-1", "bob", "b@x.io", null, encoder.encode("Secret123"));
    JWTToken token = new JWTToken("t", Instant.now().plusSeconds(60), "u-1");
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));
    when(tokenRepository.findByUserIdAndBlacklistedFalse("u-1")).thenReturn(List.of(token));

    userService.updatePassword("u-1", "NewSecret456");

    assertThat(encoder.matches("NewSecret456", user.getHashedPassword())).isTrue();
    assertThat(token.isBlacklisted()).isTrue();
  }

  @Test
  void updatePassword_malformedUserId_throwsIam4009() {
    assertCode(() -> userService.updatePassword("bad id!", "NewSecret456"), "IAM_4009");
    assertCode(() -> userService.updatePassword(null, "NewSecret456"), "IAM_4009");
  }

  @Test
  void updatePassword_unknownUser_throwsUser4001() {
    when(userRepository.findByUserId("u-404")).thenReturn(Optional.empty());
    assertCode(() -> userService.updatePassword("u-404", "NewSecret456"), "USER_4001");
  }

  @Test
  void updatePassword_weakOrMissingPassword_throwsIam4005() {
    User user = new User("u-1", "bob", "b@x.io", null, "hash");
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));

    assertCode(() -> userService.updatePassword("u-1", "weak"), "IAM_4005");
    assertCode(() -> userService.updatePassword("u-1", null), "IAM_4005");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void lockUser_disablesAccountAndRevokesTokens() {
    User user = new User("u-1", "bob", "b@x.io", null, "hash");
    JWTToken token = new JWTToken("t", Instant.now().plusSeconds(60), "u-1");
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));
    when(tokenRepository.findByUserIdAndBlacklistedFalse("u-1")).thenReturn(List.of(token));

    userService.lockUser("u-1");

    assertThat(user.isEnabled()).isFalse();
    assertThat(token.isBlacklisted()).isTrue();
  }

  @Test
  void getUserAccount_returnsFirstNameAndEnabledFlag() {
    User user = new User("u-1", "bob", "b@x.io", "Bob", "hash");
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));

    UserAccountDTO account = userService.getUserAccount("u-1");

    assertThat(account.getFirstName()).isEqualTo("Bob");
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getEmail()).isEqualTo("b@x.io");
  }

  @Test
  void requireAdminIfForwarded_noHeader_isTrusted() {
    assertThatCode(() -> userService.requireAdminIfForwarded(null, null)).doesNotThrowAnyException();
  }

  @Test
  void requireAdminIfForwarded_adminRole_passes() {
    assertThatCode(
            () -> userService.requireAdminIfForwarded("u-1", "ROLE_CUSTOMER, ROLE_ADMIN"))
        .doesNotThrowAnyException();
  }

  @Test
  void requireAdminIfForwarded_nonAdmin_throwsIam4008() {
    assertCode(() -> userService.requireAdminIfForwarded("u-1", "ROLE_CUSTOMER"), "IAM_4008");
    assertCode(() -> userService.requireAdminIfForwarded(null, ""), "IAM_4008");
  }

  @Test
  void requireSelfOrAdmin_missingOrBlankCaller_isTrusted() {
    assertThatCode(() -> userService.requireSelfOrAdmin("u-1", null, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> userService.requireSelfOrAdmin("u-1", " ", "ROLE_CUSTOMER"))
        .doesNotThrowAnyException();
  }

  @Test
  void requireSelfOrAdmin_sameCaller_passes() {
    assertThatCode(() -> userService.requireSelfOrAdmin("u-1", "u-1", "ROLE_CUSTOMER"))
        .doesNotThrowAnyException();
  }

  @Test
  void requireSelfOrAdmin_otherCallerWithAdminRole_passes() {
    assertThatCode(
            () -> userService.requireSelfOrAdmin("u-1", "admin-1", "ROLE_CUSTOMER, ROLE_ADMIN"))
        .doesNotThrowAnyException();
  }

  @Test
  void requireSelfOrAdmin_otherCallerWithoutAdminRole_throwsIam4008() {
    assertCode(() -> userService.requireSelfOrAdmin("u-1", "u-2", "ROLE_CUSTOMER"), "IAM_4008");
    assertCode(() -> userService.requireSelfOrAdmin("u-1", "u-2", null), "IAM_4008");
  }
}
