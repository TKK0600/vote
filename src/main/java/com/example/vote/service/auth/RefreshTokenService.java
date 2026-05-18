package com.example.vote.service.auth;

import com.example.vote.constant.CommonConst;
import com.example.vote.dto.auth.AuthResponse;
import com.example.vote.exception.TokenExpiredException;
import com.example.vote.exception.TokenInactiveException;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class RefreshTokenService {

    private static final long DEFAULT_EXPIRY_DAYS = 7;

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${token.refresh.expiry-days:" + DEFAULT_EXPIRY_DAYS + "}")
    private long refreshTokenExpiryDays;

    public RefreshToken createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        token.setStatus(CommonConst.ACTIVE);

        return repository.save(token);
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenStr) {
        RefreshToken refreshToken = findByTokenString(refreshTokenStr);
        verifyExpiration(refreshToken);

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId());

        revokeToken(refreshToken);
        RefreshToken newRefreshToken = createRefreshToken(user.getEmail());

        return new AuthResponse(newAccessToken, newRefreshToken.getToken());
    }

    public void verifyExpiration(RefreshToken token) {
        if (!CommonConst.ACTIVE.equals(token.getStatus())) {
            throw new TokenInactiveException("Refresh token has been revoked");
        }

        if (token.getExpiryDate().isBefore(Instant.now())) {
            repository.delete(token);
            throw new TokenExpiredException("Refresh token has expired");
        }
    }

    @Transactional
    public void logout(String refreshTokenStr, Long requestedUserId) {
        if (!StringUtils.hasText(refreshTokenStr)) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        RefreshToken token = findByTokenString(refreshTokenStr);

        if (requestedUserId != null && !token.getUser().getId().equals(requestedUserId)) {
            throw new IllegalArgumentException("Token does not belong to current user");
        }

        revokeToken(token);
        log.info("Refresh token revoked for user: {}", token.getUser().getEmail());
    }

    private RefreshToken findByTokenString(String refreshTokenStr) {
        return repository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
    }

    private void revokeToken(RefreshToken token) {
        token.setStatus(CommonConst.INACTIVE);
        repository.save(token);
    }
}
