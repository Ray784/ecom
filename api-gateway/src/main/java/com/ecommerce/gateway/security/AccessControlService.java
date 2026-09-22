package com.ecommerce.gateway.security;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.JwtClaims;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.gateway.config.GatewayProperties;
import com.ecommerce.gateway.routing.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Enforces authentication and role requirements of a route.
 *
 * <p>Tokens are validated by the IAM service. When IAM cannot answer and the local fallback is
 * enabled, the token is verified locally with the shared JwtUtil secret instead; otherwise the
 * request fails with IAM_5001.
 */
@Service
public class AccessControlService {

  private static final Logger log = LoggerFactory.getLogger(AccessControlService.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final IamAuthClient iamAuthClient;
  private final JwtUtil jwtUtil;
  private final GatewayProperties properties;

  public AccessControlService(
      IamAuthClient iamAuthClient, JwtUtil jwtUtil, GatewayProperties properties) {
    this.iamAuthClient = iamAuthClient;
    this.jwtUtil = jwtUtil;
    this.properties = properties;
  }

  /**
   * Returns the caller identity for protected routes, or null for public routes.
   *
   * @throws ApplicationBaseException with IAM_4002 for a missing or invalid token, IAM_4008 when
   *     the caller lacks the required role and IAM_5001 when IAM is unreachable without fallback
   */
  public AuthenticatedUser authorize(Route route, String authorizationHeader) {
    if (!route.authenticationRequired()) {
      return null;
    }
    AuthenticatedUser user = authenticate(authorizationHeader);
    String requiredRole = route.requiredRole();
    if (requiredRole != null && !user.hasRole(requiredRole)) {
      throw CodedErrorFactory.create(MappedError.IAM_4008, user.displayName(), requiredRole);
    }
    return user;
  }

  /** Validates a bearer Authorization header value and returns the caller identity. */
  public AuthenticatedUser authenticate(String authorizationHeader) {
    String token = extractBearerToken(authorizationHeader);
    IamValidationResult result = iamAuthClient.validate(token);
    if (result instanceof IamValidationResult.Valid valid) {
      return valid.user();
    }
    if (result instanceof IamValidationResult.Unavailable unavailable) {
      if (!properties.getAuth().isLocalFallbackEnabled()) {
        throw CodedErrorFactory.create(MappedError.IAM_5001);
      }
      log.warn("IAM validation unavailable, verifying token locally: {}", unavailable.reason());
      JwtClaims claims = jwtUtil.parseToken(token);
      return new AuthenticatedUser(claims.userId(), claims.username(), claims.roles());
    }
    throw CodedErrorFactory.create(MappedError.IAM_4002);
  }

  private static String extractBearerToken(String header) {
    if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw CodedErrorFactory.create(MappedError.IAM_4002);
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      throw CodedErrorFactory.create(MappedError.IAM_4002);
    }
    return token;
  }
}
