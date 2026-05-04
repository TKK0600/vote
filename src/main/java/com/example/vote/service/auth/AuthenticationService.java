package com.example.vote.service.auth;

import com.example.vote.dto.auth.AuthResponse;
import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.modal.auth.UserAuthProvider;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.token.VerificationToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.auth.VerificationTokenRepository;
import com.example.vote.repository.user.UserRepository;
import com.example.vote.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthenticationService {
    private static final String PROVIDER_EMAIL = "email";
    private static final String PROVIDER_GOOGLE = "google";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final MailService mailService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UserAuthProviderRepository userAuthProviderRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${token.expiry}")
    private Long tokenExpiryMinutes;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Transactional
    public ResponseEntity<String> initRegister(UserRegisterReqDTO regDto) {
        User user = userRepository.findByEmail(regDto.getEmail()).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(regDto.getEmail());
            newUser.setUserName(resolveUsernameFromEmail(regDto.getEmail()));
            newUser.setCreatedDate(LocalDateTime.now());
            return userRepository.save(newUser);
        });

        Optional<UserAuthProvider> existingEmailProvider =
                userAuthProviderRepository.findByUserIdAndProvider(user.getId(), PROVIDER_EMAIL);
        if (existingEmailProvider.isPresent()) {
            return ResponseEntity.badRequest().body("Email already registered with password login");
        }

        UserAuthProvider emailProvider = new UserAuthProvider();
        emailProvider.setUser(user);
        emailProvider.setProvider(PROVIDER_EMAIL);
        emailProvider.setEmail(user.getEmail());
        emailProvider.setPasswordHash(passwordEncoder.encode(regDto.getPassword()));
        emailProvider.setEmailVerified(false);
        userAuthProviderRepository.save(emailProvider);

        VerificationToken token = createTokenForUser(user);
        String verifyLink = frontendUrl + "/verify?token=" + token.getToken();

        mailService.sendEmail(
                user.getEmail(),
                "Email Verification",
                "Click the link to verify: " + verifyLink
        );

        return ResponseEntity.ok("Verification email sent successfully");
    }

    public ResponseEntity<String> verifyEmail(String token) {
        Optional<VerificationToken> optionalToken = verificationTokenRepository.findByToken(token);
        if (optionalToken.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid verification token");
        }

        VerificationToken verificationToken = optionalToken.get();
        User user = verificationToken.getUser();

        Optional<UserAuthProvider> emailProviderOptional =
                userAuthProviderRepository.findByUserIdAndProvider(user.getId(), PROVIDER_EMAIL);
        if (emailProviderOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("No email provider found for this user");
        }

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("Token expired");
        }

        UserAuthProvider emailProvider = emailProviderOptional.get();
        if (emailProvider.isEmailVerified()) {
            return ResponseEntity.badRequest().body("Email already verified");
        }

        emailProvider.setEmailVerified(true);
        userAuthProviderRepository.save(emailProvider);

        log.info("Email verified for user: {}", user.getEmail());
        return ResponseEntity.ok("Email verified successfully");
    }

    private VerificationToken createTokenForUser(User user) {
        verificationTokenRepository.deleteByUser(user);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        return verificationTokenRepository.save(token);
    }

    public AuthResponse login(LoginReqDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Long userId = userRepository.findByEmail(request.getEmail())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = jwtUtil.generateAccessToken(request.getEmail(), userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(request.getEmail());

        userAuthProviderRepository
                .findByProviderAndEmail(PROVIDER_EMAIL, request.getEmail())
                .ifPresent(provider -> {
                    provider.setLastLogin(LocalDateTime.now());
                    userAuthProviderRepository.save(provider);
                });

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleTokenVerifierService.GoogleUserInfo userInfo = googleTokenVerifierService.verify(idToken);

        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> createGoogleUser(userInfo));

        upsertGoogleProvider(user, userInfo);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());
        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    private User createGoogleUser(GoogleTokenVerifierService.GoogleUserInfo userInfo) {
        User user = new User();
        user.setEmail(userInfo.email());
        user.setUserName(resolveUsername(userInfo));
        user.setCreatedDate(LocalDateTime.now());
        return userRepository.save(user);
    }

    private void upsertGoogleProvider(User user, GoogleTokenVerifierService.GoogleUserInfo userInfo) {
        Optional<UserAuthProvider> byGoogleId = userAuthProviderRepository
                .findByProviderAndProviderUserId(PROVIDER_GOOGLE, userInfo.providerUserId());
        if (byGoogleId.isPresent() && !byGoogleId.get().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Google account is already linked to another user");
        }

        UserAuthProvider googleProvider = userAuthProviderRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER_GOOGLE)
                .orElseGet(() -> {
                    UserAuthProvider created = new UserAuthProvider();
                    created.setUser(user);
                    created.setProvider(PROVIDER_GOOGLE);
                    created.setEmail(user.getEmail());
                    created.setCreatedAt(LocalDateTime.now());
                    return created;
                });

        if (StringUtils.hasText(googleProvider.getProviderUserId())
                && !googleProvider.getProviderUserId().equals(userInfo.providerUserId())) {
            throw new IllegalArgumentException("Google provider id mismatch for this user");
        }

        googleProvider.setProviderUserId(userInfo.providerUserId());
        googleProvider.setEmail(user.getEmail());
        googleProvider.setEmailVerified(true);
        googleProvider.setLastLogin(LocalDateTime.now());
        userAuthProviderRepository.save(googleProvider);
    }

    private String resolveUsername(GoogleTokenVerifierService.GoogleUserInfo userInfo) {
        if (StringUtils.hasText(userInfo.name())) {
            return userInfo.name().trim();
        }
        return resolveUsernameFromEmail(userInfo.email());
    }

    private String resolveUsernameFromEmail(String email) {
        int index = email.indexOf("@");
        return index > 0 ? email.substring(0, index) : email;
    }
}
