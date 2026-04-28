package com.example.vote.service.auth;

import com.example.vote.modal.auth.UserAuthProvider;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final String PROVIDER_EMAIL = "email";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthProviderRepository userAuthProviderRepository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserAuthProvider emailProvider = userAuthProviderRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER_EMAIL)
                .orElseThrow(() -> new UsernameNotFoundException("Email login is not configured for this account"));

        if (emailProvider.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Password login is not configured for this account");
        }

        return new org.springframework.security.core.userdetails.User(user.getEmail(), emailProvider.getPasswordHash(), new ArrayList<>());
    }
}
