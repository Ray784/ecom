package com.ecommerce.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ecommerce.common.exception.ApiExternalException;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.common.exception.ApplicationBaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CodedErrorFactoryTest {

  @ParameterizedTest
  @EnumSource(MappedError.class)
  void createPicksExceptionTypeFromHttpStatusClass(MappedError error) {
    ApplicationBaseException ex = CodedErrorFactory.create(error, "a", "b");

    if (error.isServerError()) {
      assertThat(ex).isInstanceOf(ApiInternalException.class);
    } else {
      assertThat(ex).isInstanceOf(ApiExternalException.class);
    }
    assertThat(ex.getMappedError()).isSameAs(error);
    assertThat(ex.getErrorCode()).isEqualTo(error.getCode());
    assertThat(ex.getHttpStatus()).isEqualTo(error.getHttpStatus());
    assertThat(ex.getMessage()).isEqualTo(error.format("a", "b"));
  }

  @Test
  void createFormatsMessageAndKeepsArguments() {
    ApplicationBaseException ex = CodedErrorFactory.create(MappedError.ORDER_4003, "o-1", "PAID");

    assertThat(ex.getMessage()).isEqualTo("Order o-1 is in status PAID and cannot be modified/cancelled.");
    assertThat(ex.getArgs()).containsExactly("o-1", "PAID");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void createWithCauseAttachesCause() {
    RuntimeException cause = new RuntimeException("broker down");

    ApplicationBaseException ex = CodedErrorFactory.create(MappedError.ORDER_5001, cause, "o-9");

    assertThat(ex).isInstanceOf(ApiInternalException.class);
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getMessage()).isEqualTo("Failed to publish OrderPlaced event for Order o-9.");
  }

  @Test
  void createExternalForcesExternalTypeEvenForServerError() {
    ApiExternalException ex = CodedErrorFactory.createExternal(MappedError.PAY_5001);

    assertThat(ex.getHttpStatus()).isEqualTo(503);
    assertThat(ex.getErrorCode()).isEqualTo("PAY_5001");
  }

  @Test
  void createInternalForcesInternalTypeEvenForClientError() {
    ApiInternalException ex = CodedErrorFactory.createInternal(MappedError.PROD_4001, "SKU-1");

    assertThat(ex.getHttpStatus()).isEqualTo(404);
    assertThat(ex.getMessage()).isEqualTo("Product with SKU SKU-1 not found in the catalog.");
  }

  @Test
  void createInternalWithCauseAttachesCause() {
    IllegalStateException cause = new IllegalStateException("db");

    ApiInternalException ex = CodedErrorFactory.createInternal(MappedError.USER_5001, cause, "u-1");

    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getArgs()).containsExactly("u-1");
  }

  @Test
  void createRejectsNullError() {
    assertThatNullPointerException().isThrownBy(() -> CodedErrorFactory.create(null));
    assertThatNullPointerException().isThrownBy(() -> CodedErrorFactory.createExternal(null));
  }

  @Test
  void getArgsReturnsDefensiveCopy() {
    ApplicationBaseException ex = CodedErrorFactory.create(MappedError.IAM_4004, "bob");

    ex.getArgs()[0] = "mallory";

    assertThat(ex.getArgs()).containsExactly("bob");
  }

  @Test
  void nullArgumentArrayIsTreatedAsNoArguments() {
    ApplicationBaseException ex = CodedErrorFactory.create(MappedError.IAM_4001, (Object[]) null);

    assertThat(ex.getArgs()).isEmpty();
    assertThat(ex.getMessage()).isEqualTo("Invalid username or password provided.");
  }
}
