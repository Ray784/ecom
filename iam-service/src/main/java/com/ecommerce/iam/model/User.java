package com.ecommerce.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A platform account with login credentials. The table is named users because user is reserved. */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String userId;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(nullable = false)
  private String email;

  private String firstName;

  @Column(nullable = false)
  private String hashedPassword;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_pk"),
      inverseJoinColumns = @JoinColumn(name = "role_pk"))
  private Set<Role> roles = new HashSet<>();

  private boolean enabled = true;

  protected User() {}

  public User(String userId, String username, String email, String firstName, String hashedPassword) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.firstName = firstName;
    this.hashedPassword = hashedPassword;
  }

  public Long getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getHashedPassword() {
    return hashedPassword;
  }

  public void setHashedPassword(String hashedPassword) {
    this.hashedPassword = hashedPassword;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles = new HashSet<>(roles);
  }

  /** Returns the role names, for example ROLE_CUSTOMER. */
  public Set<String> getRoleNames() {
    return roles.stream().map(Role::getName).collect(Collectors.toSet());
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
