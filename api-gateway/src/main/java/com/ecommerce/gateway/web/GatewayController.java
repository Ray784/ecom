package com.ecommerce.gateway.web;

import com.ecommerce.gateway.proxy.DownstreamProxyClient;
import com.ecommerce.gateway.routing.Route;
import com.ecommerce.gateway.routing.RouteResolver;
import com.ecommerce.gateway.security.AccessControlService;
import com.ecommerce.gateway.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catch all entry point: resolves the route, enforces access control, then proxies.
 *
 * <p>Gateway errors are thrown as exceptions so the commons GlobalExceptionHandler renders them
 * as ErrorResponseDTO bodies; unknown routes become HTTP_404 and unreachable services HTTP_503.
 */
@RestController
public class GatewayController {

  private final RouteResolver routeResolver;
  private final AccessControlService accessControlService;
  private final DownstreamProxyClient proxyClient;

  public GatewayController(
      RouteResolver routeResolver,
      AccessControlService accessControlService,
      DownstreamProxyClient proxyClient) {
    this.routeResolver = routeResolver;
    this.accessControlService = accessControlService;
    this.proxyClient = proxyClient;
  }

  @RequestMapping("/**")
  public ResponseEntity<byte[]> proxy(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    String method = request.getMethod();
    String path = request.getRequestURI().substring(request.getContextPath().length());
    Route route =
        routeResolver
            .resolve(method, path)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No gateway route for " + method + " " + path + "."));
    AuthenticatedUser user =
        accessControlService.authorize(route, request.getHeader(HttpHeaders.AUTHORIZATION));
    return proxyClient.forward(route.service(), request, body, user);
  }
}
