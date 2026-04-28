package com.example.vote.repository.auth;

import com.example.vote.modal.auth.UserAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {

    Optional<UserAuthProvider> findByUserIdAndProvider(Long userId, String provider);

    Optional<UserAuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserAuthProvider> findByProviderAndEmail(String provider, String email);
}
