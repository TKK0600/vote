package com.example.vote.mapstruct.user;

import com.example.vote.config.MapStructConfig;
import com.example.vote.dto.user.UserRegisterReqDTO;
import com.example.vote.vo.user.RegistrationVo;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface UserMapStruct {

    UserRegisterReqDTO registrationVoToUserRegisterDTO(RegistrationVo registrationVo);
}
