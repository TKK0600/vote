package com.example.vote.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Step 1: Enable CORS with default settings
                // Allows browsers to make cross-origin requests
                // Uses CORS config from WebConfig.java
                .cors(withDefaults())

                // Step 2: Disable CSRF protection
                // CSRF (Cross-Site Request Forgery) requires session cookies
                // We use stateless JWT authentication, so CSRF not applicable
                // ✓ CORRECT: JWT is safer than cookies for CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // Step 3: Authorization rules
                // DEFINES WHICH ENDPOINTS REQUIRE AUTHENTICATION AND WHICH ARE PUBLIC
                // - /api/auth/** endpoints are public (registration, login, password reset)
                // - /api/user/**, /api/chat/**, /api/friend/** require authentication
                // - All other endpoints require authentication by default
                .authorizeHttpRequests(auth -> auth
//                        // Registration, login, email verification, password reset are public
//                        .requestMatchers("/api/auth/**").permitAll()
//                        // WebSocket endpoint and other health checks can be public if needed
//                        .requestMatchers("/ws").permitAll()
//                        // All other endpoints require authentication
//                        .anyRequest().authenticated()
                                .anyRequest().permitAll()
                )

                // Step 4: Session management
                // STATELESS = no server-side sessions
                // Each request must include valid JWT token
                // Good for microservices, load balancing, horizontal scaling
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

//                // Step 5: OAuth2 login configuration
//                // Handles Google OAuth2 authentication flow
//                .oauth2Login(oauth2 -> oauth2
//                        // Custom success handler after OAuth2 authentication
//                        .successHandler((request, response, authentication) -> {
//                            // Whitelist of allowed redirect URIs
//                            // Only these URLs are allowed to receive OAuth2 callback
//                            java.util.Set<String> allowedOrigins = java.util.Set.of(
//                                    "http://localhost:5173",
//                                    "http://localhost:8080"
//                            );
//
//                            String redirect = request.getParameter("redirect_uri");
//                            if (redirect == null || redirect.isBlank()) {
//                                redirect = request.getHeader("Referer");
//                            }
//
//                            // Validate redirect URI against whitelist
//                            if (redirect != null && allowedOrigins.stream().anyMatch(redirect::startsWith)) {
//                                // ✓ GOOD: Redirect is in whitelist
//                                response.sendRedirect(redirect);
//                                return;
//                            }
//
//                            // Fallback to OAuth2SuccessHandler if redirect not allowed
//                            // Uses default logic (keeps current behavior)
////                            oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);
//                        })
//                );

                // Step 6: Add JWT filter before UsernamePasswordAuthenticationFilter
                // Filter chain order matters!
                // JwtRequestFilter runs BEFORE standard authentication
                // It extracts JWT from Authorization header and sets authentication context
//                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
