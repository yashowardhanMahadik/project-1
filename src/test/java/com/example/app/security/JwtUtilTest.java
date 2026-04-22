package com.example.app.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private static final String TEST_SECRET =
            "test-secret-key-that-is-at-least-32-chars-long!!";
    private static final long EXPIRATION_MS = 86_400_000L; // 24 h

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("generateToken returns a non-null, non-empty string")
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateToken("alice");

        assertThat(token)
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("generateToken returns a compact JWT with three dot-separated segments")
    void generateToken_returnsWellFormedJwt() {
        String token = jwtUtil.generateToken("alice");

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername returns the subject used when the token was generated")
    void extractUsername_returnsCorrectSubject() {
        String username = "bob";
        String token = jwtUtil.generateToken(username);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
    }

    @Test
    @DisplayName("validateToken returns true for a freshly generated token")
    void validateToken_trueForFreshToken() {
        String token = jwtUtil.generateToken("carol");

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken returns false when the signature has been tampered with")
    void validateToken_falseForTamperedToken() {
        String token = jwtUtil.generateToken("dave");

        int lastDot = token.lastIndexOf('.');
        String tamperedToken = token.substring(0, lastDot + 1) + "AAAAAAAAAA";

        assertThat(jwtUtil.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for an obviously malformed token")
    void validateToken_falseForMalformedToken() {
        assertThat(jwtUtil.validateToken("not.a.valid.jwt.string")).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for a token signed with a different secret")
    void validateToken_falseForTokenFromDifferentSecret() {
        JwtUtil otherUtil = new JwtUtil(
                "completely-different-secret-key-at-least-32-chars!!", EXPIRATION_MS);

        String foreignToken = otherUtil.generateToken("eve");

        assertThat(jwtUtil.validateToken(foreignToken)).isFalse();
    }
}
