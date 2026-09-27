package com.c2exercise.tasktracker.dto;

import com.c2exercise.tasktracker.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        Task.Status status
) {}
