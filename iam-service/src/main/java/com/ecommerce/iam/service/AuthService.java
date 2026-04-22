package com.ecommerce.iam.service;

import com.ecommerce.common.dto.AuthResponseDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.util.JwtClaims;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.iam.model.JWTToken;
import com.ecommerce.iam.model.User;
import com.ecommerce.iam.repository.JWTTokenRepository;
import com.ecommerce.iam.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, token issuing and token validation. */
@Service
public class AuthService {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final int MAX_ISSUE_ATTEMPTS = 3;

  private final UserRepository userRepository;
  private final JWTTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  public AuthService(
      UserRepository userRepository,
      JWTTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  /**
   * Authenticates the credentials and returns a fresh JWT.
   *
   * <p>Throws IAM_4001 for blank, unknown or wrong credentials, IAM_4003 for a disabled account
   * and IAM_5001 when the identity store cannot be reached.
   */
  @Transactional
  public String login(String username, String password) {
    return authenticateAndIssue(username, password).token();
  }

  /** Same as login but wraps the token in an AuthResponseDTO for the REST layer. */
  @Transactional
  public AuthResponseDTO loginWithResponse(String username, String password) {
    IssuedToken issued = authenticateAndIssue(username, password);
    AuthResponseDTO response =
        new AuthResponseDTO(
            issued.token(),
            issued.expiresAt().toEpochMilli(),
            issued.user().getUserId(),
            issued.user().getUsername());
    response.setMessage("Login successful");
    return response;
  }

  /**
   * Validates a raw JWT and returns the current details of its owner.
   *
   * <p>Throws IAM_4002 if the token is invalid, expired, revoked or its owner no longer exists,
   * and IAM_4003 if the owner account is disabled.
   */
  @Transactional(readOnly = true)
  public UserDetailsDTO validateToken(String jwt) {
    JwtClaims claims = jwtUtil.parseToken(jwt);
    String raw = stripBearer(jwt);
    if (tokenRepository.findById(raw).map(JWTToken::isBlacklisted).orElse(false)) {
      throw CodedErrorFactory.create(MappedError.IAM_4002);
    }
    User user =
        userRepository
            .findByUserId(claims.userId())
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.IAM_4002));
    if (!user.isEnabled()) {
      throw CodedErrorFactory.create(MappedError.IAM_4003, user.getUsername());
    }
    UserDetailsDTO details =
        new UserDetailsDTO(
            user.getUserId(), user.getUsername(), user.getRoleNames(), user.getEmail(), true);
    details.setMessage("Token is valid");
    return details;
  }

  /** Signs a JWT for the user and records it so it can later be revoked. */
  @Transactional
  public String generateToken(User user) {
    return issue(user).token();
  }

  /**
   * Picks the token from an Authorization header, request body or query parameter, in that order.
   *
   * <p>A header must either use the Bearer scheme or carry the bare token. Any other scheme such
   * as Basic is rejected with IAM_4007. A missing token is rejected with IAM_4002.
   */
  public String extractToken(String authorizationHeader, String bodyToken, String paramToken) {
    if (authorizationHeader != null && !authorizationHeader.isBlank()) {
      String header = authorizationHeader.stripLeading();
      if (header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
        return requireToken(header.substring(BEARER_PREFIX.length()).trim());
      }
      header = header.stripTrailing();
      if (header.contains(" ")) {
        throw CodedErrorFactory.create(MappedError.IAM_4007);
      }
      return header;
    }
    if (bodyToken != null && !bodyToken.isBlank()) {
      return stripBearer(bodyToken.trim());
    }
    return requireToken(paramToken == null ? null : stripBearer(paramToken.trim()));
  }

  private IssuedToken authenticateAndIssue(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isEmpty()) {
      throw CodedErrorFactory.create(MappedError.IAM_4001);
    }
    Optional<User> found;
    try {
      found = userRepository.findByUsername(username.trim());
    } catch (DataAccessException e) {
      throw CodedErrorFactory.create(MappedError.IAM_5001, e);
    }
    User user =
        found
            .filter(u -> passwordEncoder.matches(password, u.getHashedPassword()))
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.IAM_4001));
    if (!user.isEnabled()) {
      throw CodedErrorFactory.create(MappedError.IAM_4003, user.getUsername());
    }
    return issue(user);
  }

  /**
   * Signs and records a token.
   *
   * <p>JwtUtil tokens have second granularity and no unique ID, so the same user can receive an
   * identical token within one second. If that identical token was already revoked, saving it
   * again would silently un-revoke it, so the method waits for the next second and re-signs.
   */
  private IssuedToken issue(User user) {
    for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
      String token =
          jwtUtil.generateToken(
              user.getUserId(), user.getUsername(), user.getRoleNames(), user.getEmail());
      if (tokenRepository.findById(token).map(JWTToken::isBlacklisted).orElse(false)) {
        sleepUntilNextSecond();
        continue;
      }
      Instant expiresAt = Instant.ofEpochMilli(jwtUtil.parseToken(token).expiresAtMillis());
      tokenRepository.save(new JWTToken(token, expiresAt, user.getUserId()));
      return new IssuedToken(token, expiresAt, user);
    }
    throw CodedErrorFactory.create(MappedError.IAM_5002, user.getUsername());
  }

  private static void sleepUntilNextSecond() {
    try {
      Thread.sleep(1000L - System.currentTimeMillis() % 1000L + 5L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String requireToken(String token) {
    if (token == null || token.isBlank()) {
      throw CodedErrorFactory.create(MappedError.IAM_4002);
    }
    return token;
  }

  private static String stripBearer(String token) {
    if (token != null && token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return token.substring(BEARER_PREFIX.length()).trim();
    }
    return token;
  }

  private record IssuedToken(String token, Instant expiresAt, User user) {}
}
