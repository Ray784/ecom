package com.ecommerce.common.error;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.springframework.stereotype.Component;

/**
 * Resolves catalog codes to localized message templates.
 *
 * <p>Localized templates are read from an optional properties resource bundle keyed by error
 * code, by default named error-messages. When no bundle or key exists for the requested locale
 * the English template defined in MappedError is used.
 */
@Component
public class ErrorMapper {

  /** Default resource bundle base name searched on the classpath. */
  public static final String DEFAULT_BUNDLE = "error-messages";

  private static final ResourceBundle.Control NO_FALLBACK =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private final String bundleBaseName;

  public ErrorMapper() {
    this(DEFAULT_BUNDLE);
  }

  public ErrorMapper(String bundleBaseName) {
    this.bundleBaseName = bundleBaseName;
  }

  /**
   * Returns the unformatted message template for the given code and locale.
   *
   * @throws IllegalArgumentException if the code is not part of the catalog
   */
  public String getErrorMessage(String code, Locale locale) {
    return getErrorMessage(resolve(code), locale);
  }

  /** Returns the unformatted message template for the given catalog entry and locale. */
  public String getErrorMessage(MappedError error, Locale locale) {
    String localized = lookupLocalized(error.getCode(), locale);
    return localized != null ? localized : error.getMessageTemplate();
  }

  /**
   * Returns the message for the given code and locale with the arguments substituted.
   *
   * @throws IllegalArgumentException if the code is not part of the catalog
   */
  public String formatErrorMessage(String code, Locale locale, Object... args) {
    return MappedError.formatTemplate(getErrorMessage(code, locale), args);
  }

  private static MappedError resolve(String code) {
    return MappedError.fromCode(code)
        .orElseThrow(() -> new IllegalArgumentException("Unknown error code: " + code));
  }

  private String lookupLocalized(String code, Locale locale) {
    if (locale == null) {
      return null;
    }
    try {
      ResourceBundle bundle = ResourceBundle.getBundle(bundleBaseName, locale, NO_FALLBACK);
      return bundle.containsKey(code) ? bundle.getString(code) : null;
    } catch (MissingResourceException e) {
      return null;
    }
  }
}
