package com.ecommerce.common.dto;

import java.util.Objects;

/** Login response carrying the generated JWT and its expiry. */
public class AuthResponseDTO extends BaseResponseDTO {

  private String jwtToken;
  private Long expirationTimestamp;
  private String userId;
  private String username;

  /** Creates an empty instance for JSON deserialization. */
  public AuthResponseDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public AuthResponseDTO(String jwtToken, Long expirationTimestamp, String userId, String username) {
    super(true, 200, "Success");
    this.jwtToken = jwtToken;
    this.expirationTimestamp = expirationTimestamp;
    this.userId = userId;
    this.username = username;
  }

  public String getJwtToken() {
    return jwtToken;
  }

  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  public Long getExpirationTimestamp() {
    return expirationTimestamp;
  }

  public void setExpirationTimestamp(Long expirationTimestamp) {
    this.expirationTimestamp = expirationTimestamp;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    AuthResponseDTO that = (AuthResponseDTO) o;
    return Objects.equals(jwtToken, that.jwtToken)
        && Objects.equals(expirationTimestamp, that.expirationTimestamp)
        && Objects.equals(userId, that.userId)
        && Objects.equals(username, that.username);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), jwtToken, expirationTimestamp, userId, username);
  }

  @Override
  public String toString() {
    return "AuthResponseDTO{" + "jwtToken=" + jwtToken
        + ", expirationTimestamp=" + expirationTimestamp
        + ", userId=" + userId
        + ", username=" + username
        + ", " + super.toString() + "}";
  }
}
