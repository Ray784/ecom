package com.ecommerce.iam.controller;

import com.ecommerce.common.dto.AuthResponseDTO;
import com.ecommerce.common.dto.LoginRequestDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.iam.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Login and token validation endpoints. */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthResponseDTO login(@RequestBody(required = false) LoginRequestDTO request) {
    if (request == null) {
      throw CodedErrorFactory.create(MappedError.IAM_4001);
    }
    return authService.loginWithResponse(request.getUsername(), request.getPassword());
  }

  /** Accepts the token from the Authorization header, a JSON body or a token query parameter. */
  @PostMapping("/validate")
  public UserDetailsDTO validate(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody(required = false) TokenRequest body,
      @RequestParam(value = "token", required = false) String token) {
    String jwt = authService.extractToken(authorization, body == null ? null : body.token(), token);
    return authService.validateToken(jwt);
  }

  /** Optional JSON body for POST /auth/validate. */
  public record TokenRequest(String token) {}
}
