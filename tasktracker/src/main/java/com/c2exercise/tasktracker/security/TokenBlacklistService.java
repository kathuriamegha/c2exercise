package com.c2exercise.tasktracker.security;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final Set<String> blacklisted = ConcurrentHashMap.newKeySet();

    public void revoke(String token) {
        blacklisted.add(token);
    }

    public boolean isRevoked(String token) {
        return blacklisted.contains(token);
    }
}
