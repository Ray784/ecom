package com.ecommerce.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A JWT issued by this service, tracked so it can be revoked before it expires. */
@Entity
@Table(name = "jwt_tokens")
public class JWTToken {

  @Id
  @Column(length = 2048)
  private String token;

  @Column(nullable = false)
  private Instant expirationDate;

  @Column(nullable = false, length = 64)
  private String userId;

  private boolean blacklisted;

  protected JWTToken() {}

  public JWTToken(String token, Instant expirationDate, String userId) {
    this.token = token;
    this.expirationDate = expirationDate;
    this.userId = userId;
  }

  public String getToken() {
    return token;
  }

  public Instant getExpirationDate() {
    return expirationDate;
  }

  public String getUserId() {
    return userId;
  }

  public boolean isBlacklisted() {
    return blacklisted;
  }

  public void setBlacklisted(boolean blacklisted) {
    this.blacklisted = blacklisted;
  }
}
