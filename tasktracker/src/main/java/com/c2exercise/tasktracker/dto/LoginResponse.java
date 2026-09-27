package com.c2exercise.tasktracker.dto;

public record LoginResponse(String token, long expiresIn) {}
