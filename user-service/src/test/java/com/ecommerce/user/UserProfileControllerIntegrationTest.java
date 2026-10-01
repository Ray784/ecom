package com.ecommerce.user;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.user.client.IamClient;
import com.ecommerce.user.client.IamUserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "iam.client.enabled=false")
@AutoConfigureMockMvc
class UserProfileControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private IamClient iamClient;

  private static String newUserId() {
    return "u-" + UUID.randomUUID();
  }

  private void createProfile(String userId, String email) throws Exception {
    mockMvc
        .perform(
            put("/profiles/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("firstName", "Ann", "lastName", "Lee", "email", email))))
        .andExpect(status().isOk());
  }

  private long addAddress(String userId, String zip, boolean isDefault) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/profiles/{id}/addresses", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "street", "1 Main St",
                                "city", "Springfield",
                                "zipCode", zip,
                                "isDefault", isDefault))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  @Test
  void putThenGet_roundTripsProfileAndAddresses() throws Exception {
    String userId = newUserId();
    createProfile(userId, userId + "@example.com");
    long addressId = addAddress(userId, "12345", false);

    mockMvc
        .perform(get("/profiles/{id}", userId).header("X-User-Id", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ann"))
        .andExpect(jsonPath("$.email").value(userId + "@example.com"))
        .andExpect(jsonPath("$.addresses[0].id").value(addressId))
        .andExpect(jsonPath("$.addresses[0].isDefault").value(true));
  }

  @Test
  void get_unknownProfile_returnsUser4001() throws Exception {
    mockMvc
        .perform(get("/profiles/{id}", "nobody"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_4001"));
  }

  @Test
  void get_missingProfileKnownToIam_isAutoInitialized() throws Exception {
    String userId = newUserId();
    when(iamClient.fetchAccount(userId))
        .thenReturn(Optional.of(new IamUserAccount(userId, "zed", userId + "@iam.io", "Zed", true)));

    mockMvc
        .perform(get("/profiles/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Zed"))
        .andExpect(jsonPath("$.email").value(userId + "@iam.io"));
  }

  @Test
  void get_iamUnreachable_returnsUser5002() throws Exception {
    String userId = newUserId();
    when(iamClient.fetchAccount(userId)).thenThrow(CodedErrorFactory.create(MappedError.USER_5002));

    mockMvc
        .perform(get("/profiles/{id}", userId))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("USER_5002"));
  }

  @Test
  void otherCaller_isDenied_butAdminIsAllowed() throws Exception {
    String userId = newUserId();
    createProfile(userId, userId + "@example.com");

    mockMvc
        .perform(
            get("/profiles/{id}", userId)
                .header("X-User-Id", "intruder")
                .header("X-User-Roles", "ROLE_CUSTOMER"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("USER_4007"));
    mockMvc
        .perform(
            delete("/profiles/{id}/addresses/{a}", userId, 1)
                .header("X-User-Id", "intruder"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("USER_4007"));
    mockMvc
        .perform(
            get("/profiles/{id}", userId)
                .header("X-User-Id", "boss")
                .header("X-User-Roles", "ROLE_ADMIN,ROLE_CUSTOMER"))
        .andExpect(status().isOk());
  }

  @Test
  void put_duplicateAndInvalidEmail_returnCatalogErrors() throws Exception {
    String first = newUserId();
    String second = newUserId();
    createProfile(first, first + "@example.com");

    mockMvc
        .perform(
            put("/profiles/{id}", second)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", first + "@example.com"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("USER_4002"));
    mockMvc
        .perform(
            put("/profiles/{id}", second)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "broken"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("USER_4004"));
  }

  @Test
  void addAddress_validationErrors() throws Exception {
    String userId = newUserId();
    createProfile(userId, userId + "@example.com");

    mockMvc
        .perform(
            post("/profiles/{id}/addresses", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("street", "", "city", "Town", "zipCode", "12345"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("USER_4005"));
    mockMvc
        .perform(
            post("/profiles/{id}/addresses", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("street", "1 Main", "city", "Town", "zipCode", "XYZ"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("USER_4009"));
  }

  @Test
  void addAddress_beyondLimit_returnsUser4003() throws Exception {
    String userId = newUserId();
    createProfile(userId, userId + "@example.com");
    for (int i = 0; i < 5; i++) {
      addAddress(userId, "12345", false);
    }

    mockMvc
        .perform(
            post("/profiles/{id}/addresses", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("street", "6 Main", "city", "Town", "zipCode", "12345"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("USER_4003"));
  }

  @Test
  void deleteAddress_defaultRulesAndNotFound() throws Exception {
    String userId = newUserId();
    createProfile(userId, userId + "@example.com");
    long defaultId = addAddress(userId, "12345", false);
    long secondId = addAddress(userId, "560001", false);

    mockMvc
        .perform(delete("/profiles/{id}/addresses/{a}", userId, defaultId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("USER_4006"));
    mockMvc
        .perform(delete("/profiles/{id}/addresses/{a}", userId, secondId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    mockMvc
        .perform(delete("/profiles/{id}/addresses/{a}", userId, secondId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_4008"));
    mockMvc
        .perform(delete("/profiles/{id}/addresses/{a}", userId, defaultId))
        .andExpect(status().isOk());
  }
}
