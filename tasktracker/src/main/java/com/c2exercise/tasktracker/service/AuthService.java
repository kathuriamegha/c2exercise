package com.c2exercise.tasktracker.service;

import com.c2exercise.tasktracker.dto.LoginRequest;
import com.c2exercise.tasktracker.dto.LoginResponse;
import com.c2exercise.tasktracker.dto.RegisterRequest;
import com.c2exercise.tasktracker.dto.UserResponse;
import com.c2exercise.tasktracker.entity.User;
import com.c2exercise.tasktracker.exception.UsernameTakenException;
import com.c2exercise.tasktracker.repository.UserRepository;
import com.c2exercise.tasktracker.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        if (userRepo.existsByUsername(req.username())) {
            throw new UsernameTakenException(req.username());
        }
        User saved = userRepo.save(new User(req.username(), passwordEncoder.encode(req.password())));
        return new UserResponse(saved.getId(), saved.getUsername());
    }

    public LoginResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new BadCredentialsException("invalid_credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BadCredentialsException("invalid_credentials");
        }
        String token = jwtUtil.generateToken(user.getUsername());
        return new LoginResponse(token, jwtUtil.getExpirationMs() / 1000);
    }
}
