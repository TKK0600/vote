package com.example.vote.mapstruct.profile;

import com.example.vote.config.MapStructConfig;
import com.example.vote.dto.profile.UserProfileReqDTO;
import com.example.vote.dto.profile.UserProfileResDTO;
import com.example.vote.modal.user.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", config = MapStructConfig.class)
public interface UserProfileMapStruct {

    @Mapping(target = "profileComplete",
             expression = "java(user.isProfileReadyForMissions() && user.getLlmProfileMarkdown() != null)")
    @Mapping(target = "scheduleComplete",
             expression = "java(user.hasScheduleData())")
    @Mapping(target = "obstacleComplete",
             expression = "java(user.hasObstacleData())")
    @Mapping(target = "aiProfileGenerated",
             expression = "java(user.getLlmProfileMarkdown() != null && !user.getLlmProfileMarkdown().isEmpty())")
    UserProfileResDTO toResDTO(User user);

    void applyProfileFields(UserProfileReqDTO req, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyNonNullFields(UserProfileReqDTO req, @MappingTarget User user);
}
