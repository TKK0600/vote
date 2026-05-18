package com.example.vote.service.auth;

import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.exception.EmailAlreadyExistsException;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.modal.auth.UserAuthProvider;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.modal.user.User;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.repository.auth.UserAuthProviderRepository;
import com.example.vote.repository.auth.VerificationTokenRepository;
import com.example.vote.repository.user.UserRepository;
import com.example.vote.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private MailService mailService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;
    @Mock
    private UserAuthProviderRepository userAuthProviderRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationService authService;

    private User testUser;
    private RefreshToken testRefreshToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "tokenExpiryMinutes", 5L);
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:8080");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");
        testUser.setUserName("user");

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId(1L);
        testRefreshToken.setToken("refresh-token-uuid");
        testRefreshToken.setUser(testUser);
    }

    // --- Registration tests ---

    @Test
    @DisplayName("Should register new user and send verification email")
    void shouldRegisterNewUser() {
        UserRegisterReqDTO dto = new UserRegisterReqDTO();
        dto.setEmail("newuser@example.com");
        dto.setPassword("securePassword123");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });
        when(userAuthProviderRepository.findByUserIdAndProvider(2L, "email")).thenReturn(Optional.empty());
        when(verificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("securePassword123")).thenReturn("$2a$hashedpassword");

        var result = authService.initRegister(dto);

        assertNotNull(result);
        assertEquals(2L, result.userId());
        assertEquals("newuser@example.com", result.email());

        verify(userAuthProviderRepository).save(any(UserAuthProvider.class));
        verify(mailService).sendEmail(eq("newuser@example.com"), eq("Email Verification"), anyString());
    }

    @Test
    @DisplayName("Should reject registration when email already has password provider")
    void shouldRejectDuplicateEmailRegistration() {
        UserRegisterReqDTO dto = new UserRegisterReqDTO();
        dto.setEmail("existing@example.com");
        dto.setPassword("securePassword123");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(testUser));
        when(userAuthProviderRepository.findByUserIdAndProvider(testUser.getId(), "email"))
                .thenReturn(Optional.of(new UserAuthProvider()));

        assertThrows(EmailAlreadyExistsException.class, () -> authService.initRegister(dto));
        verify(mailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject registration with short password")
    void shouldRejectShortPassword() {
        UserRegisterReqDTO dto = new UserRegisterReqDTO();
        dto.setEmail("user@example.com");
        dto.setPassword("1234567"); // 7 chars, below MIN_PASSWORD_LENGTH (8)

        assertThrows(IllegalArgumentException.class, () -> authService.initRegister(dto));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject registration with null password")
    void shouldRejectNullPassword() {
        UserRegisterReqDTO dto = new UserRegisterReqDTO();
        dto.setEmail("user@example.com");
        dto.setPassword(null);

        assertThrows(IllegalArgumentException.class, () -> authService.initRegister(dto));
    }

    // --- Login tests ---

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginReqDTO dto = new LoginReqDTO();
        dto.setEmail("user@example.com");
        dto.setPassword("correctPassword");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateAccessToken("user@example.com", 1L)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken("user@example.com")).thenReturn(testRefreshToken);
        when(userAuthProviderRepository.findByProviderAndEmail("email", "user@example.com"))
                .thenReturn(Optional.of(new UserAuthProvider()));

        var response = authService.login(dto);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token-uuid", response.getRefreshToken());
        verify(authenticationManager).authenticate(any());
    }

    // --- Email verification tests ---

    @Test
    @DisplayName("Should throw when verification token not found")
    void shouldThrowWhenVerificationTokenNotFound() {
        when(verificationTokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.verifyEmail("invalid"));
    }
}
