package com.c2exercise.tasktracker.dto;

import com.c2exercise.tasktracker.entity.Task;

import java.time.Instant;

public record TaskResponse(
        Long id,
        String title,
        String description,
        Task.Status status,
        Long ownerId,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getOwner().getId(),
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
