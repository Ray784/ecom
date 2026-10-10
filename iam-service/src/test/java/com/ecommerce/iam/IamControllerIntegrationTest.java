package com.ecommerce.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IamControllerIntegrationTest {

  private static final String PASSWORD = "Secret123";
  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private JsonNode register(String username) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "email", username + "@example.com",
                                "firstName", "Test"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private String login(String username, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("username", username, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("jwtToken").asText();
  }

  private static String uniqueName(String prefix) {
    return prefix + SEQ.incrementAndGet();
  }

  @Test
  void registerLoginValidate_happyPath_viaHeaderBodyAndParam() throws Exception {
    String username = uniqueName("carol");
    JsonNode registered = register(username);
    assertThat(registered.get("roles").get(0).asText()).isEqualTo("ROLE_CUSTOMER");
    String token = login(username, PASSWORD);

    mockMvc
        .perform(post("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.username").value(username))
        .andExpect(jsonPath("$.userId").value(registered.get("userId").asText()));
    mockMvc
        .perform(
            post("/auth/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
    mockMvc
        .perform(post("/auth/validate").param("token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
  }

  @Test
  void seededAdmin_canLoginWithAdminRole() throws Exception {
    String token = login("admin", "Admin@1234");

    mockMvc
        .perform(post("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[?(@ == 'ROLE_ADMIN')]").exists());
  }

  @Test
  void login_wrongPassword_returnsIam4001() throws Exception {
    String username = uniqueName("dave");
    register(username);

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", "Wrong1234"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4001"));
  }

  @Test
  void login_missingBody_returnsIam4001() throws Exception {
    mockMvc
        .perform(post("/auth/login"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4001"));
  }

  @Test
  void validate_missingToken_returnsIam4002() throws Exception {
    mockMvc
        .perform(post("/auth/validate"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"));
  }

  @Test
  void validate_basicScheme_returnsIam4007() throws Exception {
    mockMvc
        .perform(post("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4007"));
  }

  @Test
  void register_duplicateUsername_returnsIam4004() throws Exception {
    String username = uniqueName("erin");
    register(username);

    mockMvc
        .perform(
            post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("username", username, "password", PASSWORD, "email", "e@x.io"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("IAM_4004"));
  }

  @Test
  void register_weakPasswordAndMissingField_returnCatalogErrors() throws Exception {
    mockMvc
        .perform(
            post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "weakling", "password", "abc", "email", "w@x.io"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("IAM_4005"));
    mockMvc
        .perform(
            post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "noemail", "password", PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("IAM_4006"));
  }

  @Test
  void register_adminPrefix_getsAdminRole() throws Exception {
    JsonNode registered = register(uniqueName("adminops"));
    assertThat(registered.get("roles").toString()).contains("ROLE_ADMIN", "ROLE_CUSTOMER");
  }

  @Test
  void lock_thenLoginReturnsIam4003_andOldTokenIsRevoked() throws Exception {
    String username = uniqueName("frank");
    String userId = register(username).get("userId").asText();
    String token = login(username, PASSWORD);

    mockMvc.perform(post("/users/{id}/lock", userId)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", PASSWORD))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IAM_4003"));
    mockMvc
        .perform(post("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"));
  }

  @Test
  void lock_forwardedNonAdminCaller_returnsIam4008() throws Exception {
    String userId = register(uniqueName("gina")).get("userId").asText();

    mockMvc
        .perform(
            post("/users/{id}/lock", userId)
                .header("X-User-Id", "someone")
                .header("X-User-Roles", "ROLE_CUSTOMER"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IAM_4008"));
  }

  @Test
  void updatePassword_newPasswordWorks_oldTokenRevoked() throws Exception {
    String username = uniqueName("hank");
    String userId = register(username).get("userId").asText();
    String oldToken = login(username, PASSWORD);

    mockMvc
        .perform(
            put("/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Changed789"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(login(username, "Changed789")).isNotBlank();
    mockMvc
        .perform(post("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"));
  }

  @Test
  void updatePassword_malformedUserId_returnsIam4009() throws Exception {
    mockMvc
        .perform(
            put("/users/{id}/password", "bad!id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Changed789"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("IAM_4009"));
  }

  @Test
  void updatePassword_forwardedOwnCaller_succeedsAndNewPasswordLogsIn() throws Exception {
    String username = uniqueName("jill");
    String userId = register(username).get("userId").asText();

    mockMvc
        .perform(
            put("/users/{id}/password", userId)
                .header("X-User-Id", userId)
                .header("X-User-Roles", "ROLE_CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Owner4567"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(login(username, "Owner4567")).isNotBlank();
  }

  @Test
  void updatePassword_forwardedAdminCaller_canUpdateAnyUser() throws Exception {
    String username = uniqueName("kate");
    String userId = register(username).get("userId").asText();

    mockMvc
        .perform(
            put("/users/{id}/password", userId)
                .header("X-User-Id", "admin-caller")
                .header("X-User-Roles", "ROLE_CUSTOMER,ROLE_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Admin5678x"))))
        .andExpect(status().isOk());

    assertThat(login(username, "Admin5678x")).isNotBlank();
  }

  @Test
  void updatePassword_forwardedOtherNonAdminCaller_returnsIam4008() throws Exception {
    String username = uniqueName("liam");
    String userId = register(username).get("userId").asText();

    mockMvc
        .perform(
            put("/users/{id}/password", userId)
                .header("X-User-Id", "intruder")
                .header("X-User-Roles", "ROLE_CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newPassword", "Hacked1234"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IAM_4008"));

    assertThat(login(username, PASSWORD)).isNotBlank();
  }

  @Test
  void getUser_knownAndUnknown() throws Exception {
    String username = uniqueName("ivy");
    String userId = register(username).get("userId").asText();

    mockMvc
        .perform(get("/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Test"))
        .andExpect(jsonPath("$.email").value(username + "@example.com"));
    mockMvc
        .perform(get("/users/{id}", "does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("USER_4001"));
  }
}
