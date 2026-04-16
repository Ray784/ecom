package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

/** Standard body returned for every error response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponseDTO {

  private int httpStatusCode;
  private String errorCode;
  private String errorDetails;
  private String timestamp;

  /** Creates an empty instance for JSON deserialization. */
  public ErrorResponseDTO() {}

  /** Creates an instance with all fields populated. */
  public ErrorResponseDTO(int httpStatusCode, String errorCode, String errorDetails, String timestamp) {
    this.httpStatusCode = httpStatusCode;
    this.errorCode = errorCode;
    this.errorDetails = errorDetails;
    this.timestamp = timestamp;
  }

  /** Creates an error body stamped with the current instant in ISO 8601 format. */
  public static ErrorResponseDTO of(int httpStatusCode, String errorCode, String errorDetails) {
    return new ErrorResponseDTO(httpStatusCode, errorCode, errorDetails, Instant.now().toString());
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }

  public void setHttpStatusCode(int httpStatusCode) {
    this.httpStatusCode = httpStatusCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorDetails() {
    return errorDetails;
  }

  public void setErrorDetails(String errorDetails) {
    this.errorDetails = errorDetails;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorResponseDTO that = (ErrorResponseDTO) o;
    return httpStatusCode == that.httpStatusCode
        && Objects.equals(errorCode, that.errorCode)
        && Objects.equals(errorDetails, that.errorDetails)
        && Objects.equals(timestamp, that.timestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(httpStatusCode, errorCode, errorDetails, timestamp);
  }

  @Override
  public String toString() {
    return "ErrorResponseDTO{httpStatusCode=" + httpStatusCode + ", errorCode=" + errorCode
        + ", errorDetails=" + errorDetails + ", timestamp=" + timestamp + "}";
  }
}
