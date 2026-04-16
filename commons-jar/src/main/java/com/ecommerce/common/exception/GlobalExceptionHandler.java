package com.ecommerce.common.exception;

import com.ecommerce.common.dto.ErrorResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions thrown by any controller into a uniform ErrorResponseDTO body.
 *
 * <p>Registered automatically through CommonsAutoConfiguration, so services only need the
 * commons-jar dependency on their classpath.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Error code used for malformed requests that are not tied to a catalog entry. */
  public static final String BAD_REQUEST_CODE = "BAD_REQUEST";

  /** Error code used for unexpected failures that are not tied to a catalog entry. */
  public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

  /** Message returned for unexpected failures so internal details never leak to clients. */
  public static final String INTERNAL_ERROR_MESSAGE = "An unexpected internal error occurred.";

  /** Message returned when the request body cannot be parsed. */
  public static final String UNREADABLE_BODY_MESSAGE = "Request body is missing or malformed.";

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Maps catalog based exceptions to their configured HTTP status and error code. */
  @ExceptionHandler(ApplicationBaseException.class)
  public ResponseEntity<ErrorResponseDTO> handleApplicationException(ApplicationBaseException ex) {
    if (ex.getHttpStatus() >= 500) {
      log.error("Application error {}: {}", ex.getErrorCode(), ex.getMessage(), ex);
    } else {
      log.warn("Application error {}: {}", ex.getErrorCode(), ex.getMessage());
    }
    return build(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage());
  }

  /** Maps unparseable JSON bodies to a 400 response. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponseDTO> handleUnreadableBody(HttpMessageNotReadableException ex) {
    log.warn("Unreadable request body: {}", ex.getMessage());
    return build(400, BAD_REQUEST_CODE, UNREADABLE_BODY_MESSAGE);
  }

  /** Maps path or query parameters that fail type conversion to a 400 response. */
  @ExceptionHandler(TypeMismatchException.class)
  public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(TypeMismatchException ex) {
    log.warn("Parameter type mismatch: {}", ex.getMessage());
    return build(400, BAD_REQUEST_CODE, "Parameter " + ex.getPropertyName() + " has an invalid value.");
  }

  /**
   * Fallback for every other exception.
   *
   * <p>Spring MVC framework exceptions that implement ErrorResponse, such as an unknown route or
   * an unsupported HTTP method, keep their own status. Anything else becomes a generic 500.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
    if (ex instanceof ErrorResponse errorResponse) {
      int status = errorResponse.getStatusCode().value();
      String detail = errorResponse.getBody().getDetail();
      log.warn("Framework error {}: {}", status, detail);
      return build(status, "HTTP_" + status, detail != null ? detail : ex.getMessage());
    }
    log.error("Unhandled exception", ex);
    return build(500, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
  }

  private static ResponseEntity<ErrorResponseDTO> build(int status, String code, String details) {
    return ResponseEntity.status(status).body(ErrorResponseDTO.of(status, code, details));
  }
}
