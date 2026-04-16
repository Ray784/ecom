package com.ecommerce.common.util;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApiExternalException;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stateless generator and verifier for HS256 signed JSON Web Tokens.
 *
 * <p>Tokens carry the claims sub, username, email, roles, iat and exp. Instances are thread
 * safe. Any verification failure is reported as IAM_4002 so the API gateway can return the
 * catalog error directly, while signing failures are reported as IAM_5002.
 */
public class JwtUtil {

  /** Shared demo signing secret used when a service does not configure its own. */
  public static final String DEFAULT_SECRET = "ecommerce-platform-demo-hmac-sha256-signing-secret-key";

  /** Default token lifetime of one hour. */
  public static final long DEFAULT_EXPIRATION_MILLIS = 3_600_000L;

  /** Minimum secret length in bytes, matching the HS256 key size. */
  public static final int MIN_SECRET_BYTES = 32;

  private static final String ALGORITHM = "HS256";
  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final byte[] secret;
  private final long expirationMillis;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** Creates a util with the default secret and a one hour lifetime. */
  public JwtUtil() {
    this(DEFAULT_SECRET, DEFAULT_EXPIRATION_MILLIS);
  }

  public JwtUtil(String secret, long expirationMillis) {
    this(secret, expirationMillis, Clock.systemUTC());
  }

  public JwtUtil(String secret, long expirationMillis, Clock clock) {
    this(secret, expirationMillis, clock, new ObjectMapper());
  }

  JwtUtil(String secret, long expirationMillis, Clock clock, ObjectMapper objectMapper) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "JWT secret must be at least " + MIN_SECRET_BYTES + " bytes long");
    }
    if (expirationMillis <= 0) {
      throw new IllegalArgumentException("JWT expiration must be positive");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.expirationMillis = expirationMillis;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public long getExpirationMillis() {
    return expirationMillis;
  }

  /**
   * Generates a signed token for the given user.
   *
   * @throws IllegalArgumentException if userId or username is null
   * @throws ApplicationBaseException with IAM_5002 if the token cannot be serialized or signed
   */
  public String generateToken(String userId, String username, Collection<String> roles, String email) {
    if (userId == null || username == null) {
      throw new IllegalArgumentException("userId and username are required");
    }
    long nowMillis = clock.millis();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", userId);
    claims.put("username", username);
    if (email != null) {
      claims.put("email", email);
    }
    claims.put("roles", roles == null ? List.of() : roles.stream().sorted().toList());
    claims.put("iat", nowMillis / 1000L);
    claims.put("exp", (nowMillis + expirationMillis) / 1000L);
    try {
      Map<String, Object> header = new LinkedHashMap<>();
      header.put("alg", ALGORITHM);
      header.put("typ", "JWT");
      String signingInput =
          ENCODER.encodeToString(objectMapper.writeValueAsBytes(header))
              + "."
              + ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
      return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    } catch (IOException | GeneralSecurityException e) {
      throw CodedErrorFactory.createInternal(MappedError.IAM_5002, e, username);
    }
  }

  /**
   * Verifies the signature and expiry of a token and returns its claims.
   *
   * <p>A leading Bearer prefix is accepted and stripped.
   *
   * @throws ApiExternalException with IAM_4002 if the token is missing, malformed, tampered
   *     with, signed with another algorithm or key, or expired
   */
  public JwtClaims parseToken(String token) {
    if (token == null || token.isBlank()) {
      throw invalidToken();
    }
    String raw = token.startsWith(BEARER_PREFIX) ? token.substring(BEARER_PREFIX.length()) : token;
    String[] parts = raw.split("\\.", -1);
    if (parts.length != 3) {
      throw invalidToken();
    }
    try {
      Map<String, Object> header = objectMapper.readValue(DECODER.decode(parts[0]), MAP_TYPE);
      if (!ALGORITHM.equals(header.get("alg"))) {
        throw invalidToken();
      }
      byte[] expected = sign(parts[0] + "." + parts[1]);
      if (!MessageDigest.isEqual(expected, DECODER.decode(parts[2]))) {
        throw invalidToken();
      }
      Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), MAP_TYPE);
      String userId = (String) claims.get("sub");
      Number exp = (Number) claims.get("exp");
      if (userId == null || exp == null || clock.millis() / 1000L >= exp.longValue()) {
        throw invalidToken();
      }
      Number iat = (Number) claims.get("iat");
      return new JwtClaims(
          userId,
          (String) claims.get("username"),
          (String) claims.get("email"),
          toRoleSet(claims.get("roles")),
          iat == null ? 0L : iat.longValue(),
          exp.longValue());
    } catch (IOException | GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
      throw invalidToken();
    }
  }

  /** Returns true if the token passes all checks performed by parseToken. */
  public boolean validateToken(String token) {
    try {
      parseToken(token);
      return true;
    } catch (ApplicationBaseException e) {
      return false;
    }
  }

  private byte[] sign(String signingInput) throws GeneralSecurityException {
    Mac mac = Mac.getInstance(HMAC_SHA256);
    mac.init(new SecretKeySpec(secret, HMAC_SHA256));
    return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
  }

  private static Set<String> toRoleSet(Object roles) {
    if (roles == null) {
      return Set.of();
    }
    Set<String> result = new HashSet<>();
    for (Object role : (List<?>) roles) {
      result.add((String) role);
    }
    return result;
  }

  private static ApiExternalException invalidToken() {
    return CodedErrorFactory.createExternal(MappedError.IAM_4002);
  }
}
