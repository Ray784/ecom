package com.ecommerce.user.controller;

import com.ecommerce.common.dto.AddressDTO;
import com.ecommerce.common.dto.BaseResponseDTO;
import com.ecommerce.common.dto.ProfileUpdateDTO;
import com.ecommerce.common.dto.UserProfileDTO;
import com.ecommerce.user.service.UserManagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile endpoints.
 *
 * <p>Every endpoint enforces ownership through the X-User-Id and X-User-Roles headers attached by
 * the API gateway.
 */
@RestController
@RequestMapping("/profiles")
public class UserProfileController {

  static final String USER_ID_HEADER = "X-User-Id";
  static final String USER_ROLES_HEADER = "X-User-Roles";

  private final UserManagerService userManagerService;

  public UserProfileController(UserManagerService userManagerService) {
    this.userManagerService = userManagerService;
  }

  @GetMapping("/{id}")
  public UserProfileDTO getProfile(
      @PathVariable String id,
      @RequestHeader(value = USER_ID_HEADER, required = false) String callerId,
      @RequestHeader(value = USER_ROLES_HEADER, required = false) String callerRoles) {
    userManagerService.verifyAccess(id, callerId, callerRoles);
    return userManagerService.fetchUserProfile(id);
  }

  @PutMapping("/{id}")
  public UserProfileDTO putProfile(
      @PathVariable String id,
      @RequestBody(required = false) ProfileUpdateDTO update,
      @RequestHeader(value = USER_ID_HEADER, required = false) String callerId,
      @RequestHeader(value = USER_ROLES_HEADER, required = false) String callerRoles) {
    userManagerService.verifyAccess(id, callerId, callerRoles);
    return userManagerService.createOrUpdateProfile(id, update);
  }

  @PostMapping("/{id}/addresses")
  public ResponseEntity<AddressDTO> addAddress(
      @PathVariable String id,
      @RequestBody(required = false) AddressDTO address,
      @RequestHeader(value = USER_ID_HEADER, required = false) String callerId,
      @RequestHeader(value = USER_ROLES_HEADER, required = false) String callerRoles) {
    userManagerService.verifyAccess(id, callerId, callerRoles);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userManagerService.addAddress(id, address));
  }

  @DeleteMapping("/{id}/addresses/{addressId}")
  public BaseResponseDTO deleteAddress(
      @PathVariable String id,
      @PathVariable Long addressId,
      @RequestHeader(value = USER_ID_HEADER, required = false) String callerId,
      @RequestHeader(value = USER_ROLES_HEADER, required = false) String callerRoles) {
    userManagerService.verifyAccess(id, callerId, callerRoles);
    userManagerService.deleteAddress(id, addressId);
    return BaseResponseDTO.ok("Address " + addressId + " deleted");
  }
}
