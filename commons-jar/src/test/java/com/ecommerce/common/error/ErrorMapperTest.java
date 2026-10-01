package com.ecommerce.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ErrorMapperTest {

  private final ErrorMapper mapper = new ErrorMapper();

  @Test
  void getErrorMessageReturnsDefaultTemplateWhenNoBundleForLocale() {
    assertThat(mapper.getErrorMessage("IAM_4004", Locale.GERMAN)).isEqualTo("Username {0} is already taken.");
  }

  @Test
  void getErrorMessageReturnsDefaultTemplateForNullLocale() {
    assertThat(mapper.getErrorMessage("IAM_4004", null)).isEqualTo("Username {0} is already taken.");
  }

  @Test
  void getErrorMessageReturnsLocalizedTemplateFromBundle() {
    assertThat(mapper.getErrorMessage("IAM_4004", Locale.FRENCH))
        .isEqualTo("Le nom d utilisateur {0} est deja pris.");
  }

  @Test
  void getErrorMessageFallsBackWhenBundleLacksCode() {
    assertThat(mapper.getErrorMessage(MappedError.PROD_4001, Locale.FRENCH))
        .isEqualTo(MappedError.PROD_4001.getMessageTemplate());
  }

  @Test
  void getErrorMessageRejectsUnknownCode() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> mapper.getErrorMessage("BOGUS_1234", Locale.ENGLISH))
        .withMessage("Unknown error code: BOGUS_1234");
  }

  @Test
  void formatErrorMessageSubstitutesArguments() {
    assertThat(mapper.formatErrorMessage("USER_4003", Locale.ENGLISH, "u-7", 3))
        .isEqualTo("Address limit exceeded for user u-7. Maximum allowed is 3.");
  }

  @Test
  void formatErrorMessageUsesLocalizedTemplate() {
    assertThat(mapper.formatErrorMessage("IAM_4004", Locale.FRENCH, "bob"))
        .isEqualTo("Le nom d utilisateur bob est deja pris.");
  }

  @Test
  void formatErrorMessageMatchesMappedErrorFormatForDefaultLocale() {
    assertThat(mapper.formatErrorMessage("REVIEW_4002", Locale.US, "u-1", "SKU-9"))
        .isEqualTo(MappedError.REVIEW_4002.format("u-1", "SKU-9"));
  }

  @Test
  void customBundleNameIsHonoured() {
    ErrorMapper missingBundle = new ErrorMapper("does-not-exist");

    assertThat(missingBundle.getErrorMessage("IAM_4004", Locale.FRENCH))
        .isEqualTo("Username {0} is already taken.");
  }
}
