package com.ecommerce.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.dto.ErrorResponseDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @RestController
  static class ThrowingController {

    @GetMapping("/external")
    String external() {
      throw CodedErrorFactory.create(MappedError.IAM_4008, "alice", "ROLE_ADMIN");
    }

    @GetMapping("/internal")
    String internal() {
      throw CodedErrorFactory.create(MappedError.ORDER_5001, "o-1");
    }

    @PostMapping("/body")
    String body(@RequestBody Map<String, Object> payload) {
      return "ok";
    }

    @GetMapping("/typed/{id}")
    String typed(@PathVariable("id") Long id) {
      return "ok";
    }

    @GetMapping("/boom")
    String boom() {
      throw new IllegalStateException("secret internal detail");
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void clientErrorExceptionUsesCatalogStatusCodeAndFormattedMessage() throws Exception {
    mockMvc
        .perform(get("/external"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.httpStatusCode").value(403))
        .andExpect(jsonPath("$.errorCode").value("IAM_4008"))
        .andExpect(jsonPath("$.errorDetails").value("User alice does not possess the required role: ROLE_ADMIN."))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void serverErrorExceptionUsesCatalogStatusCodeAndFormattedMessage() throws Exception {
    mockMvc
        .perform(get("/internal"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.httpStatusCode").value(500))
        .andExpect(jsonPath("$.errorCode").value("ORDER_5001"))
        .andExpect(jsonPath("$.errorDetails").value("Failed to publish OrderPlaced event for Order o-1."));
  }

  @Test
  void malformedJsonBodyReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(GlobalExceptionHandler.BAD_REQUEST_CODE))
        .andExpect(jsonPath("$.errorDetails").value(GlobalExceptionHandler.UNREADABLE_BODY_MESSAGE));
  }

  @Test
  void pathVariableTypeMismatchReturnsBadRequestNamingTheParameter() throws Exception {
    mockMvc
        .perform(get("/typed/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(GlobalExceptionHandler.BAD_REQUEST_CODE))
        .andExpect(jsonPath("$.errorDetails").value("Parameter id has an invalid value."));
  }

  @Test
  void frameworkErrorResponseKeepsItsOwnStatus() throws Exception {
    mockMvc
        .perform(get("/body"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.httpStatusCode").value(405))
        .andExpect(jsonPath("$.errorCode").value("HTTP_405"))
        .andExpect(jsonPath("$.errorDetails").value("Method 'GET' is not supported."));
  }

  @Test
  void frameworkErrorResponseWithoutDetailFallsBackToExceptionMessage() {
    ErrorResponseException ex = new ErrorResponseException(HttpStatus.CONFLICT);

    ResponseEntity<ErrorResponseDTO> response = new GlobalExceptionHandler().handleGenericException(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().getErrorCode()).isEqualTo("HTTP_409");
    assertThat(response.getBody().getErrorDetails()).isEqualTo(ex.getMessage());
  }

  @Test
  void unexpectedExceptionReturnsGenericInternalErrorWithoutLeakingDetails() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.httpStatusCode").value(500))
        .andExpect(jsonPath("$.errorCode").value(GlobalExceptionHandler.INTERNAL_ERROR_CODE))
        .andExpect(jsonPath("$.errorDetails").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE));
  }
}
