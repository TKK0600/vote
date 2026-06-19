package com.example.vote.service.auth;

import com.example.vote.dto.auth.AuthResponse;
import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.dto.auth.VerificationStatusResDto;
import com.example.vote.exception.EmailAlreadyExistsException;
import com.example.vote.exception.OAuth2AuthenticationException;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.modal.auth.UserAuthProvider;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.token.VerificationToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.auth.VerificationTokenRepository;
import com.example.vote.repository.user.UserRepository;
import com.example.vote.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final MailService mailService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;

    @Value("${token.expiry}")
    private Long tokenExpiryMinutes;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Transactional
    public UserRegisterResult initRegister(UserRegisterReqDTO regDto) {
        validatePassword(regDto.getPassword());

        User user = findOrCreateUserByEmail(regDto.getEmail());

        // Check if email provider already exists (re-registration attempt)
        Optional<UserAuthProvider> existingProvider = userAuthProviderRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER_EMAIL);

        if (existingProvider.isPresent()) {
            UserAuthProvider provider = existingProvider.get();
            if (provider.isEmailVerified()) {
                // Already registered and verified → error
                throw new EmailAlreadyExistsException("Email already registered with password login");
            }
            // Registered but not verified → update password and resend verification
            provider.setPasswordHash(passwordEncoder.encode(regDto.getPassword()));
            userAuthProviderRepository.save(provider);
            sendVerificationEmail(user);
            log.info("Re-sent verification email for unverified user re-registering: {}", user.getEmail());
            return new UserRegisterResult(user.getId(), user.getEmail());
        }

        // No existing email provider → create new one
        createEmailAuthProvider(user, regDto.getPassword());
        sendVerificationEmail(user);

        return new UserRegisterResult(user.getId(), user.getEmail());
    }

    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification token expired");
        }

        User user = verificationToken.getUser();
        UserAuthProvider emailProvider = userAuthProviderRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER_EMAIL)
                .orElseThrow(() -> new IllegalArgumentException("No email provider found for this user"));

        if (emailProvider.isEmailVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        emailProvider.setEmailVerified(true);
        userAuthProviderRepository.save(emailProvider);

        log.info("Email verified for user: {}", user.getEmail());
    }

    @Transactional
    public AuthResponse login(LoginReqDTO request) {
        log.info("Login attempt for email: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User record not found"));

        // Ensure the user's email has been verified for email/password logins
        Boolean emailVerified = validateEmailVerification(user.getId());
        if (emailVerified == null || !emailVerified) {
            sendVerificationEmail(user);
            log.info("Sent verification email for unverified user attempting login: {}", user.getEmail());
            throw new IllegalArgumentException("Account not activated. A verification link has been sent to your email.");
        }

        updateLastLogin(user.getEmail());

        return generateAuthResponse(user);
    }

    @Transactional
    public String resendVarificationLink(String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Boolean isEmailVerified = validateEmailVerification(user.getId());
        if (Boolean.TRUE.equals(isEmailVerified)) {
            throw new IllegalArgumentException("User already verified");
        }

        Optional<VerificationToken> activeToken = verificationTokenRepository.findActiveTokenByUserId(user.getId(), LocalDateTime.now());
        if(activeToken.isPresent()){
            // Extend the expiry date of the active token and resend the same token
            VerificationToken token = activeToken.get();
            token.setExpiryDate(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
            verificationTokenRepository.save(token);

            String verifyLink = frontendUrl + "/api/auth/register/verify?token=" + token.getToken();
            mailService.sendEmail(
                    user.getEmail(),
                    "Email Verification",
                    "Click the link to verify: " + verifyLink);

            log.info("Extended expiry date and resent verification email for user: {}", user.getEmail());
            return "Verification email sent successfully";
        }

        // No active token: remove any existing tokens and create/send a new one
        verificationTokenRepository.deleteByUser(user);
        sendVerificationEmail(user);

        log.info("Created and sent new verification token for user: {}", user.getEmail());
        return "Verification email sent successfully";
    }

    public Boolean validateEmailVerification(Long id){
        return userAuthProviderRepository.findEmailVerified(id);
    }

    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleTokenVerifierService.GoogleUserInfo userInfo = googleTokenVerifierService.verify(idToken);

        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> createGoogleUser(userInfo));

        upsertGoogleProvider(user, userInfo);

        return generateAuthResponse(user);
    }

    // --- Private helper methods ---

    private User findOrCreateUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUserName(resolveUsernameFromEmail(email));
            newUser.setCreatedDate(LocalDateTime.now());
            return userRepository.save(newUser);
        });
    }

    private void createEmailAuthProvider(User user, String rawPassword) {
        UserAuthProvider provider = new UserAuthProvider();
        provider.setUser(user);
        provider.setProvider(PROVIDER_EMAIL);
        provider.setEmail(user.getEmail());
        provider.setPasswordHash(passwordEncoder.encode(rawPassword));
        provider.setEmailVerified(false);
        userAuthProviderRepository.save(provider);
    }

    private void sendVerificationEmail(User user) {
        VerificationToken token = createVerificationToken(user);
        String verifyLink = frontendUrl + "/api/auth/register/verify?token=" + token.getToken();

        mailService.sendEmail(
                user.getEmail(),
                "Email Verification",
                "Click the link to verify: " + verifyLink);
    }

    public VerificationStatusResDto checkVerification(String email){
        UserAuthProvider userAuth = userAuthProviderRepository.findByProviderAndEmail(PROVIDER_EMAIL, email)
                .orElseThrow(() -> new IllegalArgumentException("User record not found"));

        if(userAuth.isEmailVerified()){
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            return new VerificationStatusResDto(true, generateAuthResponse(user), "Successful verify");
        }

        return new VerificationStatusResDto(false, null, "Email not verified");
    }

    private VerificationToken createVerificationToken(User user) {
        verificationTokenRepository.deleteByUser(user);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        return verificationTokenRepository.save(token);
    }

    private void updateLastLogin(String email) {
        userAuthProviderRepository
                .findByProviderAndEmail(PROVIDER_EMAIL, email)
                .ifPresentOrElse(provider -> {
                    provider.setLastLogin(LocalDateTime.now());
                    userAuthProviderRepository.saveAndFlush(provider);
                }, () -> log.warn("No AuthProvider found to update last login for: {}", email));
    }

    private AuthResponse generateAuthResponse(User user) {
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
        validateGoogleProviderNotLinkedToOtherUser(user, userInfo);

        UserAuthProvider googleProvider = userAuthProviderRepository
                .findByUserIdAndProvider(user.getId(), PROVIDER_GOOGLE)
                .orElseGet(() -> createNewGoogleProvider(user));

        validateProviderIdMatches(googleProvider, userInfo.providerUserId());

        googleProvider.setProviderUserId(userInfo.providerUserId());
        googleProvider.setEmail(user.getEmail());
        googleProvider.setEmailVerified(true);
        googleProvider.setLastLogin(LocalDateTime.now());
        userAuthProviderRepository.save(googleProvider);
    }

    private void validateGoogleProviderNotLinkedToOtherUser(User user, GoogleTokenVerifierService.GoogleUserInfo userInfo) {
        userAuthProviderRepository
                .findByProviderAndProviderUserId(PROVIDER_GOOGLE, userInfo.providerUserId())
                .ifPresent(existing -> {
                    if (!existing.getUser().getId().equals(user.getId())) {
                        throw new OAuth2AuthenticationException("Google account is already linked to another user");
                    }
                });
    }

    private UserAuthProvider createNewGoogleProvider(User user) {
        UserAuthProvider provider = new UserAuthProvider();
        provider.setUser(user);
        provider.setProvider(PROVIDER_GOOGLE);
        provider.setEmail(user.getEmail());
        provider.setCreatedAt(LocalDateTime.now());
        return provider;
    }

    private void validateProviderIdMatches(UserAuthProvider provider, String providerUserId) {
        if (StringUtils.hasText(provider.getProviderUserId())
                && !provider.getProviderUserId().equals(providerUserId)) {
            throw new OAuth2AuthenticationException("Google provider ID mismatch for this user");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
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

    public record UserRegisterResult(Long userId, String email) {}
}
