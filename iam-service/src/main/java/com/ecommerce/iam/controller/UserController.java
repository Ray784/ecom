package com.ecommerce.iam.controller;

import com.ecommerce.common.dto.BaseResponseDTO;
import com.ecommerce.common.dto.RegistrationDTO;
import com.ecommerce.common.dto.UserDetailsDTO;
import com.ecommerce.iam.dto.UserAccountDTO;
import com.ecommerce.iam.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account registration and management endpoints. */
@RestController
@RequestMapping("/users")
public class UserController {

  static final String USER_ID_HEADER = "X-User-Id";
  static final String USER_ROLES_HEADER = "X-User-Roles";

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserDetailsDTO> register(
      @RequestBody(required = false) RegistrationDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerNewUser(request));
  }

  @GetMapping("/{userId}")
  public UserAccountDTO getUser(@PathVariable String userId) {
    return userService.getUserAccount(userId);
  }

  @PutMapping("/{userId}/password")
  public BaseResponseDTO updatePassword(
      @PathVariable String userId, @RequestBody(required = false) PasswordUpdateRequest request) {
    userService.updatePassword(userId, request == null ? null : request.newPassword());
    return BaseResponseDTO.ok("Password updated");
  }

  /** Admin and test helper that disables an account so IAM_4003 can be exercised. */
  @PostMapping("/{userId}/lock")
  public BaseResponseDTO lock(
      @PathVariable String userId,
      @RequestHeader(value = USER_ID_HEADER, required = false) String callerId,
      @RequestHeader(value = USER_ROLES_HEADER, required = false) String callerRoles) {
    userService.requireAdminIfForwarded(callerId, callerRoles);
    return userService.lockUser(userId);
  }

  /** JSON body for PUT /users/{userId}/password. */
  public record PasswordUpdateRequest(String newPassword) {}
}
