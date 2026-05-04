package com.example.vote.rest.auth;

import com.example.vote.dto.auth.AuthResponse;
import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.RefreshReqDTO;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.jwt.JwtUtil;
import com.example.vote.mapstruct.auth.UserMapStruct;
import com.example.vote.modal.token.RefreshToken;
import com.example.vote.repository.auth.RefreshTokenRepository;
import com.example.vote.service.auth.AuthenticationService;
import com.example.vote.service.auth.RefreshTokenService;
import com.example.vote.util.BoUtil;
import com.example.vote.vo.auth.GoogleLoginVo;
import com.example.vote.vo.auth.LoginVo;
import com.example.vote.vo.auth.RegistrationVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AuthenticationService authService;

    private final UserMapStruct userMapStruct;

    private final RefreshTokenRepository refreshTokenRepository;

    private final RefreshTokenService refreshTokenService;

    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public String register(@RequestBody RegistrationVo vo) {
        UserRegisterReqDTO regDto = userMapStruct.registrationVoToUserRegisterDTO(vo);
        ResponseEntity<String> response = authService.initRegister(regDto);
        if (response.getStatusCode().is2xxSuccessful()) {
            BoUtil boUtil = BoUtil.getDefaultTrueBo();
            boUtil.setMsg(response.getBody());
            return boUtil.toString();
        } else {
            BoUtil boUtil = BoUtil.getDefaultFalseBo();
            boUtil.setMsg(response.getBody());
            return boUtil.toString();
        }
    }

    @GetMapping("/register/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshReqDTO request) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow();

        refreshTokenService.verifyExpiration(refreshToken);

        String email = refreshToken.getUser().getEmail();
        Long userId = refreshToken.getUser().getId();
        String newAccessToken = jwtUtil.generateAccessToken(email, userId);

        return ResponseEntity.ok(new AuthResponse(newAccessToken, request.getRefreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginVo request) {
        LoginReqDTO loginReqDTO = userMapStruct.loginVoToReqDto(request);
        return ResponseEntity.ok(authService.login(loginReqDTO));
    }

    @PostMapping("/google/login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginVo request) {
        try {
            return ResponseEntity.ok(authService.googleLogin(request.getIdToken()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
