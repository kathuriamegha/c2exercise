package com.c2exercise.tasktracker;

import com.c2exercise.tasktracker.dto.LoginRequest;
import com.c2exercise.tasktracker.dto.RegisterRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers SPEC-001 AC-10 (token expiry) and TASK-009 logout/blacklist.
 *
 * Nested classes carry separate @TestPropertySource so expiry and logout
 * do not interfere with each other's token lifetimes.
 */
class TokenExpiryAndLogoutTest {

    // AC-10: tokens issued with a very short TTL must be rejected after expiry.
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    @TestPropertySource(properties = "app.jwt.expiration-ms=1")
    class TokenExpiryTest {

        @Autowired MockMvc mvc;
        final JsonMapper mapper = JsonMapper.builder().build();

        @Test
        void expiredToken_returns401() throws Exception {
            String token = registerAndLogin(mvc, mapper, "expiry_user", "password1");
            Thread.sleep(10); // 1ms TTL — 10ms is safely past expiry
            mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    // TASK-009: logout revokes the token; subsequent requests must be rejected.
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class LogoutTest {

        @Autowired MockMvc mvc;
        final JsonMapper mapper = JsonMapper.builder().build();

        @Test
        void logout_blacklistsToken_subsequentRequestReturns401() throws Exception {
            String token = registerAndLogin(mvc, mapper, "logout_user", "password1");

            mvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("logged_out"));

            mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

    }

    // Shared helper — static so both nested classes can use it.
    static String registerAndLogin(MockMvc mvc, JsonMapper mapper,
                                   String username, String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RegisterRequest(username, password))))
                .andExpect(status().isCreated());

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}
