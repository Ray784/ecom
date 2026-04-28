package com.ecommerce.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A postal address owned by a user. */
@Entity
@Table(name = "addresses")
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String userId;

  @Column(nullable = false)
  private String street;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false, length = 16)
  private String zipCode;

  @Column(name = "is_default")
  private boolean isDefault;

  protected Address() {}

  public Address(String userId, String street, String city, String zipCode, boolean isDefault) {
    this.userId = userId;
    this.street = street;
    this.city = city;
    this.zipCode = zipCode;
    this.isDefault = isDefault;
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getStreet() {
    return street;
  }

  public String getCity() {
    return city;
  }

  public String getZipCode() {
    return zipCode;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }
}
