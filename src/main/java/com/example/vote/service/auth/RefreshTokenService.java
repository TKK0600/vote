package com.example.vote.service.auth;

import com.example.vote.constant.CommonConst;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    private final UserRepository userRepository;

    public RefreshToken createRefreshToken(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setStatus(CommonConst.ACTIVE);

        return repository.save(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            repository.delete(token);
            throw new RuntimeException("Token expired");
        }
        return token;
    }
}
