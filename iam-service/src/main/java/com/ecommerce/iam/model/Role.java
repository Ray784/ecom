package com.ecommerce.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/** A named authority such as ROLE_CUSTOMER, ROLE_ADMIN or ROLE_SERVICE. */
@Entity
@Table(name = "roles")
public class Role {

  public static final String CUSTOMER = "ROLE_CUSTOMER";
  public static final String ADMIN = "ROLE_ADMIN";
  public static final String SERVICE = "ROLE_SERVICE";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String name;

  protected Role() {}

  public Role(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Role other)) {
      return false;
    }
    return Objects.equals(name, other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  @Override
  public String toString() {
    return name;
  }
}
