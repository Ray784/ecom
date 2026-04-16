package com.ecommerce.common.exception;

import com.ecommerce.common.error.MappedError;

/** Server side failure such as a database, Kafka or downstream dependency error. */
public class ApiInternalException extends ApplicationBaseException {

  public ApiInternalException(MappedError mappedError, Object... args) {
    super(mappedError, args);
  }

  public ApiInternalException(MappedError mappedError, Throwable cause, Object... args) {
    super(mappedError, cause, args);
  }
}
