package com.example.vote.service.profile;

import com.example.vote.dto.profile.ProfileGenerationResDTO;
import com.example.vote.dto.profile.ProfileStatusResDTO;
import com.example.vote.dto.profile.UserProfileReqDTO;
import com.example.vote.dto.profile.UserProfileResDTO;
import com.example.vote.exception.BusinessException;
import com.example.vote.mapstruct.profile.UserProfileMapStruct;
import com.example.vote.modal.user.User;
import com.example.vote.repository.user.UserRepository;
import com.example.vote.util.PromptLoader;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String PROFILE_GENERATION_PROMPT = PromptLoader.load("prompt/profile_generation_prompt.md");
    private static final int MAX_PROFILE_LENGTH = 10_000;

    private final UserRepository userRepository;
    private final ChatClient chatClient;
    private final UserProfileMapStruct userProfileMapStruct;

    public UserProfileResDTO getProfile(Long userId) {
        User user = findUserById(userId);
        return userProfileMapStruct.toResDTO(user);
    }

    @Transactional
    public UserProfileResDTO saveProfile(Long userId, UserProfileReqDTO req) {
        User user = findUserById(userId);
        userProfileMapStruct.applyProfileFields(req, user);
        userRepository.save(user);
        return userProfileMapStruct.toResDTO(user);
    }

    @Transactional
    public UserProfileResDTO updateProfile(Long userId, UserProfileReqDTO req) {
        User user = findUserById(userId);
        userProfileMapStruct.applyNonNullFields(req, user);
        userRepository.save(user);
        return userProfileMapStruct.toResDTO(user);
    }

    public ProfileStatusResDTO getProfileStatus(Long userId) {
        User user = findUserById(userId);
        return new ProfileStatusResDTO(
            user.hasScheduleData(),
            user.hasObstacleData(),
            user.getDailyStructure() != null,
            user.getLlmProfileMarkdown() != null && !user.getLlmProfileMarkdown().isEmpty(),
            user.isProfileReadyForMissions() && user.getLlmProfileMarkdown() != null
        );
    }

    @Transactional
    public ProfileGenerationResDTO generateProfile(Long userId) {
        User user = findUserById(userId);

        // Validate mandatory fields before generation
        if (user.getDailyStructure() == null) {
            throw new BusinessException("PROFILE_INCOMPLETE", "Daily structure is required before generating profile");
        }
        if (user.getBlockedTime() == null || user.getBlockedTime().isBlank()) {
            throw new BusinessException("PROFILE_INCOMPLETE", "Blocked time is required before generating profile");
        }
        if (user.getFreeTimeWindows() == null || user.getFreeTimeWindows().isBlank()) {
            throw new BusinessException("PROFILE_INCOMPLETE", "Free time windows are required before generating profile");
        }
        if (user.getEnergyPeak() == null) {
            throw new BusinessException("PROFILE_INCOMPLETE", "Energy peak is required before generating profile");
        }

        // Build prompt with all profile data
        String prompt = String.format(PROFILE_GENERATION_PROMPT,
            safeValue(user.getAge()),
            safeValue(user.getGender()),
            safeValue(user.getHeight()),
            safeValue(user.getWeight()),
            safeValue(user.getActivityLevel()),
            safeValue(user.getOccupation()),
            safeValue(user.getDailyStructure()),
            safeValue(user.getDailyStructureCustom()),
            safeValue(user.getBlockedTime()),
            safeValue(user.getFreeTimeWindows()),
            safeValue(user.getEnergyPeak()),
            safeValue(user.getWeekendAvailability()),
            safeValue(user.getMainObstacle()),
            safeValue(user.getEnvironmentNotes())
        );

        String aiResponse = chatClient.prompt()
                .system(prompt)
                .user("Generate my lifestyle profile based on my answers.")
                .call()
                .content();

        // Validate AI output
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new BusinessException("PROFILE_GENERATION_FAILED", "AI returned an empty profile. Please try again.");
        }
        if (aiResponse.length() > MAX_PROFILE_LENGTH) {
            aiResponse = aiResponse.substring(0, MAX_PROFILE_LENGTH);
        }

        // Save to user entity
        user.setLlmProfileMarkdown(aiResponse);
        user.setLlmProfileUpdatedAt(LocalDateTime.now());
        user.setProfileComplete(true);
        userRepository.save(user);

        return new ProfileGenerationResDTO(true, aiResponse, user.getLlmProfileUpdatedAt());
    }

    // ========== Private helpers ==========

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private String safeValue(Object value) {
        return value != null ? value.toString() : "Not provided";
    }
}
