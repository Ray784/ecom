package com.ecommerce.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApiExternalException;
import com.ecommerce.common.exception.ApiInternalException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtUtilTest {

  private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";
  private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();

  private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000L, CLOCK);

  @Test
  void generatedTokenRoundTripsAllClaims() {
    String token = jwtUtil.generateToken("u-1", "alice", List.of("ROLE_CUSTOMER", "ROLE_ADMIN"), "a@x.com");

    JwtClaims claims = jwtUtil.parseToken(token);

    assertThat(token.split("\\.")).hasSize(3);
    assertThat(claims.userId()).isEqualTo("u-1");
    assertThat(claims.username()).isEqualTo("alice");
    assertThat(claims.email()).isEqualTo("a@x.com");
    assertThat(claims.roles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    assertThat(claims.hasRole("ROLE_ADMIN")).isTrue();
    assertThat(claims.hasRole("ROLE_SERVICE")).isFalse();
    assertThat(claims.issuedAt()).isEqualTo(NOW.getEpochSecond());
    assertThat(claims.expiresAt()).isEqualTo(NOW.getEpochSecond() + 60);
    assertThat(claims.expiresAtMillis()).isEqualTo(NOW.toEpochMilli() + 60_000L);
  }

  @Test
  void tokenWithoutEmailOrRolesParsesWithEmptyValues() {
    JwtClaims claims = jwtUtil.parseToken(jwtUtil.generateToken("u-2", "bob", null, null));

    assertThat(claims.email()).isNull();
    assertThat(claims.roles()).isEmpty();
  }

  @Test
  void bearerPrefixIsAccepted() {
    String token = jwtUtil.generateToken("u-1", "alice", Set.of("ROLE_CUSTOMER"), null);

    assertThat(jwtUtil.parseToken("Bearer " + token).userId()).isEqualTo("u-1");
  }

  @Test
  void expiredTokenIsRejected() {
    String token = jwtUtil.generateToken("u-1", "alice", Set.of(), null);
    JwtUtil later = new JwtUtil(SECRET, 60_000L, Clock.offset(CLOCK, Duration.ofSeconds(60)));

    assertInvalid(() -> later.parseToken(token));
    assertThat(later.validateToken(token)).isFalse();
  }

  @Test
  void tokenSignedWithDifferentSecretIsRejected() {
    JwtUtil other = new JwtUtil("another-secret-that-is-also-32-bytes-long", 60_000L, CLOCK);
    String token = other.generateToken("u-1", "alice", Set.of(), null);

    assertInvalid(() -> jwtUtil.parseToken(token));
  }

  @Test
  void tamperedPayloadIsRejected() {
    String[] parts = jwtUtil.generateToken("u-1", "alice", Set.of("ROLE_CUSTOMER"), null).split("\\.");
    String forgedPayload = encode("{\"sub\":\"u-1\",\"roles\":[\"ROLE_ADMIN\"],\"exp\":9999999999}");

    assertInvalid(() -> jwtUtil.parseToken(parts[0] + "." + forgedPayload + "." + parts[2]));
  }

  @Test
  void algorithmOtherThanHs256IsRejectedEvenWithValidSignature() throws Exception {
    String header = encode("{\"alg\":\"none\"}");
    String payload = encode("{\"sub\":\"u-1\",\"exp\":9999999999}");

    assertInvalid(() -> jwtUtil.parseToken(signed(header, payload)));
  }

  @Test
  void signedTokenMissingSubjectIsRejected() throws Exception {
    assertInvalid(() -> jwtUtil.parseToken(signed(hs256Header(), encode("{\"exp\":9999999999}"))));
  }

  @Test
  void signedTokenMissingExpiryIsRejected() throws Exception {
    assertInvalid(() -> jwtUtil.parseToken(signed(hs256Header(), encode("{\"sub\":\"u-1\"}"))));
  }

  @Test
  void signedTokenWithWrongClaimTypeIsRejected() throws Exception {
    String payload = encode("{\"sub\":\"u-1\",\"exp\":9999999999,\"roles\":\"ROLE_ADMIN\"}");

    assertInvalid(() -> jwtUtil.parseToken(signed(hs256Header(), payload)));
  }

  @Test
  void signedTokenWithoutIssuedAtDefaultsToZero() throws Exception {
    JwtClaims claims = jwtUtil.parseToken(signed(hs256Header(), encode("{\"sub\":\"u-1\",\"exp\":9999999999}")));

    assertThat(claims.issuedAt()).isZero();
    assertThat(claims.username()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "only.two", "a.b.c.d", "!!!.@@@.###", "e30.e30.e30"})
  void malformedTokensAreRejected(String token) {
    assertInvalid(() -> jwtUtil.parseToken(token));
    assertThat(jwtUtil.validateToken(token)).isFalse();
  }

  @Test
  void nonJsonHeaderIsRejected() {
    assertInvalid(() -> jwtUtil.parseToken(encode("not-json") + ".e30.e30"));
  }

  @Test
  void validateTokenReturnsTrueForValidToken() {
    assertThat(jwtUtil.validateToken(jwtUtil.generateToken("u-1", "alice", Set.of(), null))).isTrue();
  }

  @Test
  void generateTokenRequiresUserIdAndUsername() {
    assertThatIllegalArgumentException().isThrownBy(() -> jwtUtil.generateToken(null, "alice", Set.of(), null));
    assertThatIllegalArgumentException().isThrownBy(() -> jwtUtil.generateToken("u-1", null, Set.of(), null));
  }

  @Test
  void serializationFailureIsReportedAsIam5002() throws Exception {
    ObjectMapper failing = mock(ObjectMapper.class);
    when(failing.writeValueAsBytes(any())).thenThrow(JsonProcessingException.class);
    JwtUtil broken = new JwtUtil(SECRET, 60_000L, CLOCK, failing);

    assertThatThrownBy(() -> broken.generateToken("u-1", "alice", Set.of(), null))
        .isInstanceOf(ApiInternalException.class)
        .hasMessage("Error generating the JWT token for user alice.")
        .extracting("mappedError")
        .isEqualTo(MappedError.IAM_5002);
  }

  @Test
  void defaultConstructorUsesDefaultSecretAndLifetime() {
    JwtUtil defaults = new JwtUtil();

    assertThat(defaults.getExpirationMillis()).isEqualTo(JwtUtil.DEFAULT_EXPIRATION_MILLIS);
    String token = defaults.generateToken("u-1", "alice", Set.of(), null);
    assertThat(new JwtUtil(JwtUtil.DEFAULT_SECRET, 1L).validateToken(token)).isTrue();
  }

  @Test
  void constructorRejectsShortOrNullSecret() {
    assertThatIllegalArgumentException().isThrownBy(() -> new JwtUtil("too-short", 1000L));
    assertThatIllegalArgumentException().isThrownBy(() -> new JwtUtil(null, 1000L));
  }

  @Test
  void constructorRejectsNonPositiveExpiration() {
    assertThatIllegalArgumentException().isThrownBy(() -> new JwtUtil(SECRET, 0L));
  }

  @Test
  void constructorRejectsNullClock() {
    assertThatNullPointerException().isThrownBy(() -> new JwtUtil(SECRET, 1000L, null));
  }

  private static void assertInvalid(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(ApiExternalException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("IAM_4002", 401);
  }

  private static String encode(String json) {
    return ENC.encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private static String hs256Header() {
    return encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
  }

  private static String signed(String header, String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String input = header + "." + payload;
    return input + "." + ENC.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
  }
}
