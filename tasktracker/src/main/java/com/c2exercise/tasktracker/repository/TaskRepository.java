package com.c2exercise.tasktracker.repository;

import com.c2exercise.tasktracker.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByOwnerId(Long ownerId);
    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}
