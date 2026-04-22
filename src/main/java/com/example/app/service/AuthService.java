package com.example.app.service;

import com.example.app.exception.UserAlreadyExistsException;
import com.example.app.model.dto.AuthResponse;
import com.example.app.model.dto.LoginRequest;
import com.example.app.model.dto.RegisterRequest;
import com.example.app.model.entity.User;
import com.example.app.repository.UserRepository;
import com.example.app.security.JwtUtil;
import com.example.app.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TokenBlacklistService tokenBlacklistService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", user.getUsername());

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token = jwtUtil.generateToken(request.username());
        log.info("User logged in: {}", request.username());
        return new AuthResponse(token);
    }

    public void logout(String bearerToken) {
        String token = extractRawToken(bearerToken);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Missing or malformed Authorization header.");
        }
        long remainingTtl = jwtUtil.getRemainingTtlMs(token);
        tokenBlacklistService.blacklistToken(token, remainingTtl);
        log.info("Token blacklisted successfully.");
    }

    private String extractRawToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}
