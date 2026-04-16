package com.ecommerce.common.dto;

import java.util.Objects;

/** Non sensitive profile fields a user may update. */
public class ProfileUpdateDTO {

  private String firstName;
  private String lastName;
  private String email;

  /** Creates an empty instance for JSON deserialization. */
  public ProfileUpdateDTO() {}

  /** Creates an instance with all fields populated. */
  public ProfileUpdateDTO(String firstName, String lastName, String email) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProfileUpdateDTO that = (ProfileUpdateDTO) o;
    return Objects.equals(firstName, that.firstName)
        && Objects.equals(lastName, that.lastName)
        && Objects.equals(email, that.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(firstName, lastName, email);
  }

  @Override
  public String toString() {
    return "ProfileUpdateDTO{" + "firstName=" + firstName
        + ", lastName=" + lastName
        + ", email=" + email + "}";
  }
}
