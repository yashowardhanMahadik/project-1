package com.example.app.controller;

import com.example.app.model.dto.AuthResponse;
import com.example.app.model.dto.LoginRequest;
import com.example.app.model.dto.RegisterRequest;
import com.example.app.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the /auth/** endpoints.
 *
 * - Uses H2 in-memory via application-test.yml (see src/test/resources/).
 * - Mocks TokenBlacklistService so no Redis connection is required.
 * - DirtiesContext ensures the H2 schema is recreated between test-class runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    // =================================================================
    // Helper: register a user and return the issued token
    // =================================================================

    private String registerAndGetToken(String username, String password, String email)
            throws Exception {
        RegisterRequest req = new RegisterRequest(username, password, email);

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())   // 201
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.token();
    }

    // =================================================================
    // POST /auth/register
    // =================================================================

    @Test
    @DisplayName("POST /auth/register — valid payload returns 201 and a non-blank token")
    void register_validPayload_returns201WithToken() throws Exception {
        RegisterRequest req = new RegisterRequest("user1", "pass123", "user1@test.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())   // 201
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/register — duplicate username returns 409")
    void register_duplicateUsername_returns409() throws Exception {
        registerAndGetToken("dupuser", "pass123", "dup@test.com");

        RegisterRequest duplicate = new RegisterRequest("dupuser", "other", "dup2@test.com");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict());   // 409
    }

    // =================================================================
    // POST /auth/login
    // =================================================================

    @Test
    @DisplayName("POST /auth/login — valid credentials return 200 and a non-blank token")
    void login_validCredentials_returns200WithToken() throws Exception {
        registerAndGetToken("loginuser", "mypassword", "loginuser@test.com");

        LoginRequest loginReq = new LoginRequest("loginuser", "mypassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /auth/login — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        registerAndGetToken("wrongpassuser", "correctpass", "wp@test.com");

        LoginRequest loginReq = new LoginRequest("wrongpassuser", "wrongpass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());  // 401
    }

    @Test
    @DisplayName("POST /auth/login — unknown username returns 401")
    void login_unknownUser_returns401() throws Exception {
        LoginRequest loginReq = new LoginRequest("ghost", "pass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================
    // POST /auth/logout
    // =================================================================

    @Test
    @DisplayName("POST /auth/logout — valid Bearer token returns 204")
    void logout_withValidToken_returns204() throws Exception {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);

        String token = registerAndGetToken("logoutuser", "pass", "logout@test.com");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());   // 204

        verify(tokenBlacklistService).blacklistToken(eq(token), anyLong());
    }

    @Test
    @DisplayName("POST /auth/logout — missing Bearer token returns 401")
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());  // 401
    }

    // =================================================================
    // GET /actuator/health
    // =================================================================

    @Test
    @DisplayName("GET /actuator/health — no auth required, returns 200")
    void actuatorHealth_returns200WithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // =================================================================
    // Protected endpoint — no token
    // =================================================================

    @Test
    @DisplayName("GET /some-protected-endpoint — no token returns 401 or 403")
    void protectedEndpoint_withoutToken_returnsUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/some-protected-endpoint"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("Expected 401 or 403 for unauthenticated request")
                            .isIn(401, 403);
                });
    }
}
