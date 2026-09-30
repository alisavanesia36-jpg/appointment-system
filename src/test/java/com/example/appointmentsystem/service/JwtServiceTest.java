package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final String secret =
            "a-very-long-secret-key-for-hs256-signing-0123456789";

    private final JwtService jwtService = new JwtService(secret, 3600);

    @Test
    void generateToken_extractUsername_roundtrip() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRole("USER");

        String token = jwtService.generateToken(user);

        assertEquals("alice", jwtService.extractUsername(token));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(jwtService.validateToken("not-a-valid-token"));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtService expiredService = new JwtService(secret, -1);
        User user = new User();
        user.setUsername("bob");
        user.setRole("USER");

        String token = expiredService.generateToken(user);

        assertFalse(expiredService.validateToken(token));
    }
}
