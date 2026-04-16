package com.ecommerce.common.exception;

import com.ecommerce.common.error.MappedError;

/** Client facing failure whose error code and message are safe to return to API consumers. */
public class ApiExternalException extends ApplicationBaseException {

  public ApiExternalException(MappedError mappedError, Object... args) {
    super(mappedError, args);
  }

  public ApiExternalException(MappedError mappedError, Throwable cause, Object... args) {
    super(mappedError, cause, args);
  }
}
