package com.example.app.service;

import com.example.app.exception.UserAlreadyExistsException;
import com.example.app.model.dto.AuthResponse;
import com.example.app.model.dto.LoginRequest;
import com.example.app.model.dto.RegisterRequest;
import com.example.app.model.entity.User;
import com.example.app.repository.UserRepository;
import com.example.app.security.JwtUtil;
import com.example.app.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository        userRepository;
    @Mock private JwtUtil               jwtUtil;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil,
                tokenBlacklistService, authenticationManager);
    }

    // =================================================================
    // register()
    // =================================================================

    @Test
    @DisplayName("register: success — persists user with encoded password and returns token")
    void register_success_savesUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("alice", "secret", "alice@example.com");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder()
                    .id(1L)
                    .username(u.getUsername())
                    .password(u.getPassword())
                    .email(u.getEmail())
                    .build();
        });
        when(jwtUtil.generateToken("alice")).thenReturn("jwt-token-alice");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token-alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPassword()).isEqualTo("encoded-secret");

        verify(passwordEncoder).encode("secret");
        verify(jwtUtil).generateToken("alice");
    }

    @Test
    @DisplayName("register: duplicate username throws UserAlreadyExistsException")
    void register_duplicateUsername_throwsUserAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest("alice", "secret", "alice2@example.com");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(anyString());
    }

    // =================================================================
    // login()
    // =================================================================

    @Test
    @DisplayName("login: valid credentials returns AuthResponse with token")
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest("bob", "password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("bob", "password"));
        when(jwtUtil.generateToken("bob")).thenReturn("jwt-token-bob");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("jwt-token-bob");
        verify(jwtUtil).generateToken("bob");
    }

    @Test
    @DisplayName("login: wrong password throws BadCredentialsException")
    void login_wrongPassword_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("bob", "wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("login: unknown username throws BadCredentialsException")
    void login_unknownUsername_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest("nobody", "password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generateToken(anyString());
    }

    // =================================================================
    // logout()
    // =================================================================

    @Test
    @DisplayName("logout: passes the raw token to TokenBlacklistService.blacklistToken")
    void logout_callsBlacklistWithCorrectToken() {
        String rawToken = "some.jwt.token";
        when(jwtUtil.getRemainingTtlMs(rawToken)).thenReturn(1000L);

        authService.logout(rawToken);

        verify(tokenBlacklistService).blacklistToken(eq(rawToken), eq(1000L));
    }
}
