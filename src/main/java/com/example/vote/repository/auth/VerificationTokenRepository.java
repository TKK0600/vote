package com.example.vote.repository.auth;

import com.example.vote.modal.user.User;
import com.example.vote.modal.token.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    void deleteByUser(User user);
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.user.id = :userId AND vt.expiryDate > :now")
    Optional<VerificationToken> findActiveTokenByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
