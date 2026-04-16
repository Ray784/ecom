package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Shipping or billing address of a user. */
public class AddressDTO {

  private Long id;
  private String street;
  private String city;
  private String zipCode;
  private boolean isDefault;

  /** Creates an empty instance for JSON deserialization. */
  public AddressDTO() {}

  /** Creates an instance with all fields populated. */
  public AddressDTO(Long id, String street, String city, String zipCode, boolean isDefault) {
    this.id = id;
    this.street = street;
    this.city = city;
    this.zipCode = zipCode;
    this.isDefault = isDefault;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getStreet() {
    return street;
  }

  public void setStreet(String street) {
    this.street = street;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getZipCode() {
    return zipCode;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  @JsonProperty("isDefault")
  public boolean isDefault() {
    return isDefault;
  }

  @JsonProperty("isDefault")
  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddressDTO that = (AddressDTO) o;
    return Objects.equals(id, that.id)
        && Objects.equals(street, that.street)
        && Objects.equals(city, that.city)
        && Objects.equals(zipCode, that.zipCode)
        && isDefault == that.isDefault;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, street, city, zipCode, isDefault);
  }

  @Override
  public String toString() {
    return "AddressDTO{" + "id=" + id
        + ", street=" + street
        + ", city=" + city
        + ", zipCode=" + zipCode
        + ", isDefault=" + isDefault + "}";
  }
}
