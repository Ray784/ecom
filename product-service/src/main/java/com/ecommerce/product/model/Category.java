package com.ecommerce.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Product category such as ELECTRONICS or BOOKS. */
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false)
  private String name;

  public Category() {}

  public Category(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
