package com.example.vote.repository.auth;

import com.example.vote.modal.auth.UserAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {

    Optional<UserAuthProvider> findByUserIdAndProvider(Long userId, String provider);

    Optional<UserAuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserAuthProvider> findByProviderAndEmail(String provider, String email);

    @Query("SELECT MAX(u.lastLogin) FROM UserAuthProvider u WHERE u.user.id = :userId")
    LocalDateTime findMaxLastLoginByUserId(@Param("userId") Long userId);
}
