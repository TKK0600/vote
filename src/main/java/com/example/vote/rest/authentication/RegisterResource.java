package com.example.vote.rest.authentication;

import com.example.vote.dto.user.UserRegisterReqDTO;
import com.example.vote.mapstruct.user.UserMapStruct;
import com.example.vote.service.user.Authentication;
import com.example.vote.util.BoUtil;
import com.example.vote.vo.user.RegistrationVo;
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
public class RegisterResource {

    private final Authentication registrationService;

    private final UserMapStruct userMapStruct;

    @PostMapping("/register")
    public String register(@RequestBody RegistrationVo vo) {
        UserRegisterReqDTO regDto = userMapStruct.registrationVoToUserRegisterDTO(vo);
        ResponseEntity<String> response = registrationService.initRegister(regDto);
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
        return registrationService.verifyEmail(token);
    }
}
