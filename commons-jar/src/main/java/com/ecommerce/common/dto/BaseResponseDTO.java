package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * Standard container for all synchronous API responses.
 *
 * <p>Service specific response DTOs extend this class and add their payload fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseResponseDTO {

  private boolean success;
  private int statusCode;
  private String message;

  /** Creates an empty instance for JSON deserialization. */
  public BaseResponseDTO() {}

  /** Creates a response with the given outcome, HTTP status and message. */
  public BaseResponseDTO(boolean success, int statusCode, String message) {
    this.success = success;
    this.statusCode = statusCode;
    this.message = message;
  }

  /** Returns a successful response with status 200 and the given message. */
  public static BaseResponseDTO ok(String message) {
    return new BaseResponseDTO(true, 200, message);
  }

  /** Returns a successful response with status 201 and the given message. */
  public static BaseResponseDTO created(String message) {
    return new BaseResponseDTO(true, 201, message);
  }

  /** Returns a failed response with the given HTTP status and message. */
  public static BaseResponseDTO failure(int statusCode, String message) {
    return new BaseResponseDTO(false, statusCode, message);
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BaseResponseDTO that = (BaseResponseDTO) o;
    return success == that.success
        && statusCode == that.statusCode
        && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, statusCode, message);
  }

  @Override
  public String toString() {
    return "success=" + success + ", statusCode=" + statusCode + ", message=" + message;
  }
}
