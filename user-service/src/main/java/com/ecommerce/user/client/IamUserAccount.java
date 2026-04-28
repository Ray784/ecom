package com.ecommerce.user.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of the IAM account returned by GET /users/{userId} on iam-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IamUserAccount(
    String userId, String username, String email, String firstName, boolean enabled) {}
