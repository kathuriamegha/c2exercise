package com.c2exercise.tasktracker.controller;

import com.c2exercise.tasktracker.dto.TaskRequest;
import com.c2exercise.tasktracker.dto.TaskResponse;
import com.c2exercise.tasktracker.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal UserDetails user) {
        return taskService.listForUser(user.getUsername());
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody TaskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(user.getUsername(), req));
    }

    @GetMapping("/{id}")
    public TaskResponse getOne(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return taskService.getOne(user.getUsername(), id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest req) {
        return taskService.update(user.getUsername(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        taskService.delete(user.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
