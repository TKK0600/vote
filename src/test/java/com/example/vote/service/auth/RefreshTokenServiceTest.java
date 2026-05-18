package com.example.vote.service.auth;

import com.example.vote.constant.CommonConst;
import com.example.vote.exception.TokenExpiredException;
import com.example.vote.exception.TokenInactiveException;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;
    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");

        activeToken = new RefreshToken();
        activeToken.setId(1L);
        activeToken.setToken("test-uuid-token");
        activeToken.setUser(testUser);
        activeToken.setExpiryDate(Instant.now().plus(7, ChronoUnit.DAYS));
        activeToken.setStatus(CommonConst.ACTIVE);

        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiryDays", 7L);
    }

    @Test
    @DisplayName("Should create refresh token with ACTIVE status and future expiry")
    void shouldCreateRefreshToken() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(repository.save(any(RefreshToken.class))).thenReturn(activeToken);

        RefreshToken result = refreshTokenService.createRefreshToken("user@example.com");

        assertNotNull(result);
        assertEquals(CommonConst.ACTIVE, result.getStatus());
        assertEquals(testUser, result.getUser());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertEquals(CommonConst.ACTIVE, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getToken());
        assertTrue(captor.getValue().getExpiryDate().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Should throw when creating token for non-existent user")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.createRefreshToken("nonexistent@example.com"));
    }

    @Test
    @DisplayName("Should verify active non-expired token successfully")
    void shouldVerifyActiveToken() {
        assertDoesNotThrow(() -> refreshTokenService.verifyExpiration(activeToken));
    }

    @Test
    @DisplayName("Should throw TokenInactiveException for revoked token")
    void shouldThrowForInactiveToken() {
        activeToken.setStatus(CommonConst.INACTIVE);

        assertThrows(TokenInactiveException.class,
                () -> refreshTokenService.verifyExpiration(activeToken));
    }

    @Test
    @DisplayName("Should throw TokenExpiredException and delete expired token")
    void shouldThrowAndDeleteForExpiredToken() {
        activeToken.setExpiryDate(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThrows(TokenExpiredException.class,
                () -> refreshTokenService.verifyExpiration(activeToken));
        verify(repository).delete(activeToken);
    }

    @Test
    @DisplayName("Should revoke token on logout")
    void shouldRevokeTokenOnLogout() {
        when(repository.findByToken("test-uuid-token")).thenReturn(Optional.of(activeToken));

        refreshTokenService.logout("test-uuid-token", 1L);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertEquals(CommonConst.INACTIVE, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should throw when logout with mismatched userId")
    void shouldThrowWhenLogoutUserIdMismatch() {
        when(repository.findByToken("test-uuid-token")).thenReturn(Optional.of(activeToken));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.logout("test-uuid-token", 999L));
    }

    @Test
    @DisplayName("Should throw when logout with empty token string")
    void shouldThrowWhenLogoutWithEmptyToken() {
        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.logout("", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.logout(null, 1L));
    }

    @Test
    @DisplayName("Should throw when refresh token not found")
    void shouldThrowWhenTokenNotFound() {
        when(repository.findByToken("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.logout("nonexistent", 1L));
    }

    @Test
    @DisplayName("Should rotate tokens on refresh — old revoked, new issued")
    void shouldRotateTokensOnRefresh() {
        when(repository.findByToken("test-uuid-token")).thenReturn(Optional.of(activeToken));
        when(jwtUtil.generateAccessToken("user@example.com", 1L)).thenReturn("new-access-token");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        // Capture the new refresh token created during rotation
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            // This is the new token being created by createRefreshToken (status is set inside)
            t.setId(2L);
            return t;
        });

        var response = refreshTokenService.refreshAccessToken("test-uuid-token");

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotEquals("test-uuid-token", response.getRefreshToken());

        // Verify old token was revoked
        verify(repository, atLeastOnce()).save(argThat(t ->
                CommonConst.INACTIVE.equals(t.getStatus()) && "test-uuid-token".equals(t.getToken())));
    }
}
