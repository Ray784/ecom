package com.ecommerce.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.AuthResponseDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.iam.model.JWTToken;
import com.ecommerce.iam.model.Role;
import com.ecommerce.iam.model.User;
import com.ecommerce.iam.repository.JWTTokenRepository;
import com.ecommerce.iam.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private static final String PASSWORD = "Secret123";

  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final JwtUtil jwtUtil = new JwtUtil();
  private UserRepository userRepository;
  private JWTTokenRepository tokenRepository;
  private AuthService authService;
  private User alice;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    tokenRepository = mock(JWTTokenRepository.class);
    authService = new AuthService(userRepository, tokenRepository, encoder, jwtUtil);
    alice = new User("u-1", "alice", "alice@example.com", "Alice", encoder.encode(PASSWORD));
    alice.setRoles(Set.of(new Role(Role.CUSTOMER)));
    when(tokenRepository.save(any(JWTToken.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  void login_validCredentials_returnsParsableTokenAndPersistsIt() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

    String token = authService.login("alice", PASSWORD);

    assertThat(jwtUtil.parseToken(token).userId()).isEqualTo("u-1");
    verify(tokenRepository).save(any(JWTToken.class));
  }

  @Test
  void loginWithResponse_populatesAuthResponse() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

    AuthResponseDTO response = authService.loginWithResponse("alice", PASSWORD);

    assertThat(response.getUserId()).isEqualTo("u-1");
    assertThat(response.getUsername()).isEqualTo("alice");
    assertThat(response.getJwtToken()).isNotBlank();
    assertThat(response.getExpirationTimestamp()).isGreaterThan(System.currentTimeMillis());
  }

  @Test
  void login_blankCredentials_throwsIam4001() {
    assertCode(() -> authService.login(" ", PASSWORD), "IAM_4001");
    assertCode(() -> authService.login("alice", null), "IAM_4001");
  }

  @Test
  void login_unknownUser_throwsIam4001() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertCode(() -> authService.login("ghost", PASSWORD), "IAM_4001");
  }

  @Test
  void login_wrongPassword_throwsIam4001() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    assertCode(() -> authService.login("alice", "Wrong1234"), "IAM_4001");
  }

  @Test
  void login_disabledAccount_throwsIam4003() {
    alice.setEnabled(false);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    assertCode(() -> authService.login("alice", PASSWORD), "IAM_4003");
  }

  @Test
  void login_identityStoreDown_throwsIam5001() {
    when(userRepository.findByUsername("alice"))
        .thenThrow(new DataAccessResourceFailureException("db down"));
    assertCode(() -> authService.login("alice", PASSWORD), "IAM_5001");
  }

  @Test
  void validateToken_validToken_returnsCurrentUserDetails() {
    String token = jwtUtil.generateToken("u-1", "alice", Set.of(Role.CUSTOMER), null);
    when(tokenRepository.findById(token)).thenReturn(Optional.empty());
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(alice));

    UserDetailsDTO details = authService.validateToken(token);

    assertThat(details.isValid()).isTrue();
    assertThat(details.getUserId()).isEqualTo("u-1");
    assertThat(details.getRoles()).containsExactly(Role.CUSTOMER);
    assertThat(details.getEmail()).isEqualTo("alice@example.com");
  }

  @Test
  void validateToken_malformedToken_throwsIam4002() {
    assertCode(() -> authService.validateToken("not-a-jwt"), "IAM_4002");
  }

  @Test
  void validateToken_blacklistedToken_throwsIam4002() {
    String token = jwtUtil.generateToken("u-1", "alice", Set.of(), null);
    JWTToken stored = new JWTToken(token, Instant.now().plusSeconds(60), "u-1");
    stored.setBlacklisted(true);
    when(tokenRepository.findById(token)).thenReturn(Optional.of(stored));
    assertCode(() -> authService.validateToken(token), "IAM_4002");
  }

  @Test
  void validateToken_deletedUser_throwsIam4002() {
    String token = jwtUtil.generateToken("u-9", "gone", Set.of(), null);
    when(tokenRepository.findById(token)).thenReturn(Optional.empty());
    when(userRepository.findByUserId("u-9")).thenReturn(Optional.empty());
    assertCode(() -> authService.validateToken(token), "IAM_4002");
  }

  @Test
  void validateToken_disabledUser_throwsIam4003() {
    alice.setEnabled(false);
    String token = jwtUtil.generateToken("u-1", "alice", Set.of(), null);
    when(tokenRepository.findById(token)).thenReturn(Optional.empty());
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(alice));
    assertCode(() -> authService.validateToken(token), "IAM_4003");
  }

  @Test
  void generateToken_embedsRolesAndEmail() {
    String token = authService.generateToken(alice);

    assertThat(jwtUtil.parseToken(token).roles()).containsExactly(Role.CUSTOMER);
    assertThat(jwtUtil.parseToken(token).email()).isEqualTo("alice@example.com");
  }

  @Test
  void generateToken_identicalTokenAlreadyRevoked_resignsInNextSecond() {
    JWTToken revoked = new JWTToken("x", Instant.now().plusSeconds(60), "u-1");
    revoked.setBlacklisted(true);
    when(tokenRepository.findById(any(String.class)))
        .thenReturn(Optional.of(revoked))
        .thenReturn(Optional.empty());

    String token = authService.generateToken(alice);

    assertThat(jwtUtil.parseToken(token).userId()).isEqualTo("u-1");
    verify(tokenRepository).save(any(JWTToken.class));
  }

  @Test
  void generateToken_revokedCollisionPersists_throwsIam5002() {
    JWTToken revoked = new JWTToken("x", Instant.now().plusSeconds(60), "u-1");
    revoked.setBlacklisted(true);
    when(tokenRepository.findById(any(String.class))).thenReturn(Optional.of(revoked));

    assertCode(() -> authService.generateToken(alice), "IAM_5002");
  }

  @Test
  void extractToken_bearerHeader_stripsScheme() {
    assertThat(authService.extractToken("Bearer abc.def.ghi", null, null)).isEqualTo("abc.def.ghi");
    assertThat(authService.extractToken("bearer abc", null, null)).isEqualTo("abc");
  }

  @Test
  void extractToken_bareHeader_isReturnedAsIs() {
    assertThat(authService.extractToken("abc.def.ghi", "ignored", null)).isEqualTo("abc.def.ghi");
  }

  @Test
  void extractToken_otherScheme_throwsIam4007() {
    assertCode(() -> authService.extractToken("Basic dXNlcjpwYXNz", null, null), "IAM_4007");
  }

  @Test
  void extractToken_fallsBackToBodyThenParam() {
    assertThat(authService.extractToken(null, "Bearer body", "param")).isEqualTo("body");
    assertThat(authService.extractToken(" ", null, "param")).isEqualTo("param");
  }

  @Test
  void extractToken_missingEverywhere_throwsIam4002() {
    assertCode(() -> authService.extractToken(null, null, null), "IAM_4002");
    assertCode(() -> authService.extractToken("Bearer ", null, null), "IAM_4002");
  }
}
