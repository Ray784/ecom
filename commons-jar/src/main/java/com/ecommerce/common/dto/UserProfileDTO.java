package com.ecommerce.common.dto;

import java.util.List;
import java.util.Objects;

/** Complete user profile together with saved addresses. */
public class UserProfileDTO extends BaseResponseDTO {

  private String userId;
  private String firstName;
  private String lastName;
  private String email;
  private List<AddressDTO> addresses;

  /** Creates an empty instance for JSON deserialization. */
  public UserProfileDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public UserProfileDTO(String userId, String firstName, String lastName, String email, List<AddressDTO> addresses) {
    super(true, 200, "Success");
    this.userId = userId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.addresses = addresses;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public List<AddressDTO> getAddresses() {
    return addresses;
  }

  public void setAddresses(List<AddressDTO> addresses) {
    this.addresses = addresses;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    UserProfileDTO that = (UserProfileDTO) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(firstName, that.firstName)
        && Objects.equals(lastName, that.lastName)
        && Objects.equals(email, that.email)
        && Objects.equals(addresses, that.addresses);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, firstName, lastName, email, addresses);
  }

  @Override
  public String toString() {
    return "UserProfileDTO{" + "userId=" + userId
        + ", firstName=" + firstName
        + ", lastName=" + lastName
        + ", email=" + email
        + ", addresses=" + addresses
        + ", " + super.toString() + "}";
  }
}
