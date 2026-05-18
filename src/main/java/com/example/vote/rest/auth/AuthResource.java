package com.example.vote.rest.auth;

import com.example.vote.constant.CommonConst;
import com.example.vote.dto.auth.AuthResponse;
import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.mapstruct.auth.UserMapStruct;
import com.example.vote.service.auth.AuthenticationService;
import com.example.vote.service.auth.AuthenticationService.UserRegisterResult;
import com.example.vote.service.auth.RefreshTokenService;
import com.example.vote.util.BoUtil;
import com.example.vote.vo.auth.GoogleLoginVo;
import com.example.vote.vo.auth.LoginVo;
import com.example.vote.vo.auth.RefreshTokenVo;
import com.example.vote.vo.auth.RegistrationVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(maxAge = 3600)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AuthenticationService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapStruct userMapStruct;

    @PostMapping("/register")
    public String register(@Valid @RequestBody RegistrationVo vo) {
        UserRegisterReqDTO regDto = userMapStruct.registrationVoToUserRegisterDTO(vo);
        UserRegisterResult result = authService.initRegister(regDto);

        BoUtil bo = BoUtil.getDefaultTrueBo();
        bo.setMsg("Verification email sent successfully");
        bo.setData(result);
        return bo.toString();
    }

    @GetMapping("/register/verify")
    public String verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);

        BoUtil bo = BoUtil.getDefaultTrueBo();
        bo.setMsg("Email verified successfully");
        return bo.toString();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenVo request) {
        AuthResponse response = refreshTokenService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginVo request) {
        LoginReqDTO loginReqDTO = userMapStruct.loginVoToReqDto(request);
        return ResponseEntity.ok(authService.login(loginReqDTO));
    }

    @PostMapping("/google/login")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginVo request) {
        return ResponseEntity.ok(authService.googleLogin(request.getIdToken()));
    }

    @PostMapping("/logout")
    public String logout(@RequestBody RefreshTokenVo request, HttpServletRequest httpServletRequest) {
        Object rawUserId = httpServletRequest.getAttribute(CommonConst.REQUEST_USER_ID_ATTR);
        Long requestUserId = rawUserId instanceof Integer
                ? ((Integer) rawUserId).longValue()
                : (Long) rawUserId;

        refreshTokenService.logout(request.getRefreshToken(), requestUserId);

        BoUtil bo = BoUtil.getDefaultTrueBo();
        bo.setMsg("Logged out successfully");
        return bo.toString();
    }
}
