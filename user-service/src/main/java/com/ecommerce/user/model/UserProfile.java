package com.ecommerce.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Personal details of a platform user, keyed by the IAM user ID. */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

  @Id
  @Column(length = 64)
  private String userId;

  private String firstName;

  private String lastName;

  @Column(unique = true)
  private String email;

  protected UserProfile() {}

  public UserProfile(String userId, String firstName, String lastName, String email) {
    this.userId = userId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  public String getUserId() {
    return userId;
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
}
