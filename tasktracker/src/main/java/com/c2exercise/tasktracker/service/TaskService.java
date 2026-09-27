package com.c2exercise.tasktracker.service;

import com.c2exercise.tasktracker.dto.TaskRequest;
import com.c2exercise.tasktracker.dto.TaskResponse;
import com.c2exercise.tasktracker.entity.Task;
import com.c2exercise.tasktracker.entity.User;
import com.c2exercise.tasktracker.exception.TaskNotFoundException;
import com.c2exercise.tasktracker.repository.TaskRepository;
import com.c2exercise.tasktracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepo;
    private final UserRepository userRepo;

    public TaskService(TaskRepository taskRepo, UserRepository userRepo) {
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
    }

    public List<TaskResponse> listForUser(String username) {
        User owner = resolveUser(username);
        return taskRepo.findAllByOwnerId(owner.getId()).stream()
                .map(TaskResponse::from).toList();
    }

    @Transactional
    public TaskResponse create(String username, TaskRequest req) {
        User owner = resolveUser(username);
        Task saved = taskRepo.save(new Task(req.title(), req.description(), req.status(), owner));
        return TaskResponse.from(saved);
    }

    public TaskResponse getOne(String username, Long id) {
        User owner = resolveUser(username);
        return taskRepo.findByIdAndOwnerId(id, owner.getId())
                .map(TaskResponse::from)
                .orElseGet(() -> {
                    // task exists but belongs to another user → 403
                    if (taskRepo.existsById(id)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    }
                    throw new TaskNotFoundException(id);
                });
    }

    @Transactional
    public TaskResponse update(String username, Long id, TaskRequest req) {
        User owner = resolveUser(username);
        Task task = resolveTaskWithOwnerGuard(id, owner.getId());
        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.status() != null) task.setStatus(req.status());
        return TaskResponse.from(taskRepo.save(task));
    }

    @Transactional
    public void delete(String username, Long id) {
        User owner = resolveUser(username);
        Task task = resolveTaskWithOwnerGuard(id, owner.getId());
        taskRepo.delete(task);
    }

    private User resolveUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    private Task resolveTaskWithOwnerGuard(Long taskId, Long ownerId) {
        if (!taskRepo.existsById(taskId)) throw new TaskNotFoundException(taskId);
        return taskRepo.findByIdAndOwnerId(taskId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
