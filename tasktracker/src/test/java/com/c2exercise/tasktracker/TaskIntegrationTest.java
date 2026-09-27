package com.c2exercise.tasktracker;

import com.c2exercise.tasktracker.dto.LoginRequest;
import com.c2exercise.tasktracker.dto.RegisterRequest;
import com.c2exercise.tasktracker.dto.TaskRequest;
import com.c2exercise.tasktracker.entity.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers SPEC-001 ACs: AC-6, AC-7, AC-8, AC-9
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskIntegrationTest {

    @Autowired MockMvc mvc;
    final JsonMapper mapper = JsonMapper.builder().build();

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setup() throws Exception {
        aliceToken = registerAndLogin("alice", "password1");
        bobToken   = registerAndLogin("bob",   "password2");
    }

    // AC-6: create task returns 201 with full DTO
    @Test
    void createTask_returns201WithDto() throws Exception {
        mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TaskRequest("Buy groceries", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Buy groceries"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.ownerId").isNumber());
    }

    // AC-7: GET /api/tasks returns only the authenticated user's tasks
    @Test
    void listTasks_returnsOnlyOwnTasks() throws Exception {
        mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TaskRequest("Alice task", null, null))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TaskRequest("Bob task", null, null))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Alice task"));
    }

    // AC-8: PUT on another user's task returns 403
    @Test
    void updateTask_anotherOwner_returns403() throws Exception {
        Long taskId = createTask(aliceToken, "Alice's task");

        mvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TaskRequest("Stolen!", null, Task.Status.DONE))))
                .andExpect(status().isForbidden());
    }

    // AC-9: DELETE removes task, returns 204
    @Test
    void deleteTask_returns204() throws Exception {
        Long taskId = createTask(aliceToken, "To delete");

        mvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    // Helper: register + login, return JWT
    private String registerAndLogin(String username, String password) throws Exception {
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

    // Helper: create a task, return its id
    private Long createTask(String token, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new TaskRequest(title, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
