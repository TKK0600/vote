package com.example.vote.service.user;

import com.example.vote.dto.user.UserRegisterReqDTO;
import com.example.vote.modal.user.User;
import com.example.vote.modal.user.VerificationToken;
import com.example.vote.repository.user.UserRepository;
import com.example.vote.repository.user.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class Authentication {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final VerificationTokenRepository verificationTokenRepository;

    private final MailService mailService;

    @Value("${token.expiry}")
    private Long tokenExpiryMinutes;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Transactional
    public ResponseEntity<String> initRegister(UserRegisterReqDTO regDto) {
        Optional<User> existing = userRepository.findByEmail(regDto.getEmail());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body("Email already registered");
        }

        User user = new User();
        user.setEmail(regDto.getEmail());
        String hashPassword = passwordEncoder.encode(regDto.getPassword());
        user.setPassword(hashPassword);

        user.setEmailVerified(false);
        user.setCreatedDate(LocalDateTime.now());

        userRepository.save(user);

        VerificationToken token = createTokenForUser(user);

        String verifyLink = frontendUrl + "/verify?token=" + token.getToken();

        mailService.sendEmail(
                user.getEmail(),
                "Email Verification",
                "Click the link to verify: " + verifyLink
        );

        return ResponseEntity.ok("Verification email sent successfully");
    }

    public ResponseEntity<String> verifyEmail(String token){
        Optional<VerificationToken> optionalToken = verificationTokenRepository.findByToken(token);
        if(optionalToken.isEmpty()){
            return ResponseEntity.badRequest().body("Invalid verification token");
        }

        VerificationToken verificationToken = optionalToken.get();
        User user = verificationToken.getUser();

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Token expired");
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.badRequest().body("Email already verified");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());

        return ResponseEntity.ok("Email verified successfully");
    }

    private VerificationToken createTokenForUser(User user) {
        // Prevents accumulation of expired tokens
        verificationTokenRepository.deleteByUser(user);

        // Create new token with UUID and expiry time
        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        // Token expires in TOKEN_EXPIRY_MINUTES (5 minutes)
        token.setExpiryDate(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));

        return verificationTokenRepository.save(token);
    }
}
