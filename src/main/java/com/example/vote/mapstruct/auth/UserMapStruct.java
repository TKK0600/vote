package com.example.vote.mapstruct.auth;

import com.example.vote.config.MapStructConfig;
import com.example.vote.dto.auth.LoginReqDTO;
import com.example.vote.dto.auth.ResendEmailDto;
import com.example.vote.dto.auth.UserRegisterReqDTO;
import com.example.vote.vo.auth.LoginVo;
import com.example.vote.vo.auth.RegistrationVo;
import com.example.vote.vo.auth.ResendEmailVo;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface UserMapStruct {

    UserRegisterReqDTO registrationVoToUserRegisterDTO(RegistrationVo registrationVo);

    LoginReqDTO loginVoToReqDto(LoginVo loginVo);

    ResendEmailDto resendVotoReqDto(ResendEmailVo vo);
}
