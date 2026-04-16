package com.ecommerce.common.exception;

import com.ecommerce.common.error.MappedError;

import java.util.Objects;

/**
 * Root of all application exceptions.
 *
 * <p>Carries the catalog entry that caused the failure together with the arguments used to
 * render its message template, so handlers can produce a consistent error response.
 */
public abstract class ApplicationBaseException extends RuntimeException {

  private static final Object[] NO_ARGS = new Object[0];

  private final transient MappedError mappedError;
  private final String errorCode;
  private final int httpStatus;
  private final transient Object[] args;

  protected ApplicationBaseException(MappedError mappedError, Object... args) {
    this(mappedError, null, args);
  }

  protected ApplicationBaseException(MappedError mappedError, Throwable cause, Object... args) {
    super(Objects.requireNonNull(mappedError, "mappedError").format(args), cause);
    this.mappedError = mappedError;
    this.errorCode = mappedError.getCode();
    this.httpStatus = mappedError.getHttpStatus();
    this.args = args == null ? NO_ARGS : args.clone();
  }

  public MappedError getMappedError() {
    return mappedError;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  /** Returns a defensive copy of the template arguments. */
  public Object[] getArgs() {
    return args.clone();
  }
}
