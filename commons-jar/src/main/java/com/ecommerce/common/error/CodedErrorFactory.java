package com.ecommerce.common.error;

import com.ecommerce.common.exception.ApiExternalException;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.common.exception.ApplicationBaseException;

import java.util.Objects;

/**
 * Creates service exceptions from catalog entries.
 *
 * <p>The create methods choose the exception type from the HTTP status: 4xx entries become
 * ApiExternalException and 5xx entries become ApiInternalException.
 */
public final class CodedErrorFactory {

  private CodedErrorFactory() {}

  /** Creates the exception type matching the HTTP status class of the given error. */
  public static ApplicationBaseException create(MappedError error, Object... args) {
    return create(error, null, args);
  }

  /** Same as create but attaches the underlying cause. */
  public static ApplicationBaseException create(MappedError error, Throwable cause, Object... args) {
    Objects.requireNonNull(error, "error");
    return error.isServerError()
        ? new ApiInternalException(error, cause, args)
        : new ApiExternalException(error, cause, args);
  }

  /** Always creates an ApiExternalException regardless of the HTTP status. */
  public static ApiExternalException createExternal(MappedError error, Object... args) {
    return new ApiExternalException(error, args);
  }

  /** Always creates an ApiInternalException regardless of the HTTP status. */
  public static ApiInternalException createInternal(MappedError error, Object... args) {
    return new ApiInternalException(error, args);
  }

  /** Always creates an ApiInternalException that wraps the given cause. */
  public static ApiInternalException createInternal(MappedError error, Throwable cause, Object... args) {
    return new ApiInternalException(error, cause, args);
  }
}
