package com.ecommerce.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.gateway.config.GatewayProperties;
import com.ecommerce.gateway.routing.Route;
import com.ecommerce.gateway.routing.ServiceId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessControlServiceTest {

  private static final AuthenticatedUser CUSTOMER =
      new AuthenticatedUser("u1", "alice", Set.of(Route.ROLE_CUSTOMER));

  private IamAuthClient iamAuthClient;
  private JwtUtil jwtUtil;
  private GatewayProperties properties;
  private AccessControlService service;

  @BeforeEach
  void setUp() {
    iamAuthClient = mock(IamAuthClient.class);
    jwtUtil = new JwtUtil();
    properties = new GatewayProperties();
    service = new AccessControlService(iamAuthClient, jwtUtil, properties);
  }

  private static void assertError(Runnable call, String code, int status) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", code)
        .hasFieldOrPropertyWithValue("httpStatus", status);
  }

  @Test
  void authorize_publicRoute_returnsNullWithoutCallingIam() {
    assertThat(service.authorize(Route.publicRoute(ServiceId.PRODUCT), null)).isNull();
    verifyNoInteractions(iamAuthClient);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "Basic abc", "Bearer", "Bearer    ", "Token abc"})
  void authorize_missingOrMalformedHeader_throwsIam4002(String header) {
    assertError(
        () -> service.authorize(Route.authenticated(ServiceId.PAYMENT), header), "IAM_4002", 401);
    verifyNoInteractions(iamAuthClient);
  }

  @Test
  void authorize_lowercaseBearerPrefix_isAccepted() {
    when(iamAuthClient.validate("tok")).thenReturn(new IamValidationResult.Valid(CUSTOMER));

    assertThat(service.authorize(Route.authenticated(ServiceId.PAYMENT), "bearer tok"))
        .isEqualTo(CUSTOMER);
  }

  @Test
  void authorize_iamValidWithRequiredRole_returnsUser() {
    when(iamAuthClient.validate("tok")).thenReturn(new IamValidationResult.Valid(CUSTOMER));

    AuthenticatedUser user =
        service.authorize(Route.withRole(ServiceId.ORDER, Route.ROLE_CUSTOMER), "Bearer tok");

    assertThat(user).isEqualTo(CUSTOMER);
    verify(iamAuthClient).validate("tok");
  }

  @Test
  void authorize_iamValidWithoutRequiredRole_throwsIam4008() {
    when(iamAuthClient.validate("tok")).thenReturn(new IamValidationResult.Valid(CUSTOMER));

    assertThatThrownBy(
            () ->
                service.authorize(Route.withRole(ServiceId.PRODUCT, Route.ROLE_ADMIN), "Bearer tok"))
        .isInstanceOf(ApplicationBaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", "IAM_4008")
        .hasFieldOrPropertyWithValue("httpStatus", 403)
        .hasMessage("User alice does not possess the required role: ROLE_ADMIN.");
  }

  @Test
  void authorize_userWithoutUsername_usesUserIdInIam4008Message() {
    when(iamAuthClient.validate("tok"))
        .thenReturn(new IamValidationResult.Valid(new AuthenticatedUser("u9", null, Set.of())));

    assertThatThrownBy(
            () ->
                service.authorize(Route.withRole(ServiceId.PRODUCT, Route.ROLE_ADMIN), "Bearer tok"))
        .hasMessageContaining("User u9 ");
  }

  @Test
  void authorize_iamRejected_throwsIam4002EvenWithLocallyValidToken() {
    String token = jwtUtil.generateToken("u1", "alice", List.of(Route.ROLE_CUSTOMER), null);
    when(iamAuthClient.validate(token)).thenReturn(new IamValidationResult.Rejected());

    assertError(
        () -> service.authorize(Route.authenticated(ServiceId.PAYMENT), "Bearer " + token),
        "IAM_4002",
        401);
  }

  @Test
  void authorize_iamUnavailableWithFallback_verifiesLocally() {
    String token = jwtUtil.generateToken("u1", "alice", List.of(Route.ROLE_CUSTOMER), null);
    when(iamAuthClient.validate(token)).thenReturn(new IamValidationResult.Unavailable("down"));

    AuthenticatedUser user =
        service.authorize(Route.withRole(ServiceId.ORDER, Route.ROLE_CUSTOMER), "Bearer " + token);

    assertThat(user).isEqualTo(new AuthenticatedUser("u1", "alice", Set.of(Route.ROLE_CUSTOMER)));
  }

  @Test
  void authorize_iamUnavailableWithFallbackAndBadToken_throwsIam4002() {
    when(iamAuthClient.validate(anyString())).thenReturn(new IamValidationResult.Unavailable("down"));

    assertError(
        () -> service.authorize(Route.authenticated(ServiceId.PAYMENT), "Bearer not.a.jwt"),
        "IAM_4002",
        401);
  }

  @Test
  void authorize_iamUnavailableWithoutFallback_throwsIam5001() {
    properties.getAuth().setLocalFallbackEnabled(false);
    when(iamAuthClient.validate(anyString())).thenReturn(new IamValidationResult.Unavailable("down"));

    assertError(
        () -> service.authorize(Route.authenticated(ServiceId.PAYMENT), "Bearer tok"),
        "IAM_5001",
        503);
  }
}
