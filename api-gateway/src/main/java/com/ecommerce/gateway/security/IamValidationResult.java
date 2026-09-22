package com.ecommerce.gateway.security;

/** Outcome of asking the IAM service to validate a token. */
public sealed interface IamValidationResult {

  /** IAM confirmed the token and returned the caller identity. */
  record Valid(AuthenticatedUser user) implements IamValidationResult {}

  /** IAM explicitly rejected the token. */
  record Rejected() implements IamValidationResult {}

  /** IAM could not give a usable answer, for example because it is down. */
  record Unavailable(String reason) implements IamValidationResult {}
}
