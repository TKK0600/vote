package com.example.vote.rest.profile;

import com.example.vote.dto.profile.UserProfileReqDTO;
import com.example.vote.dto.profile.UserProfileResDTO;
import com.example.vote.dto.profile.ProfileGenerationResDTO;
import com.example.vote.dto.profile.ProfileStatusResDTO;
import com.example.vote.service.profile.UserProfileService;
import com.example.vote.util.RequestUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserResource {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResDTO> getProfile() {
        Long userId = RequestUserUtil.getCurrentUserId();
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    @PostMapping("/profile")
    public ResponseEntity<UserProfileResDTO> saveProfile(
            @RequestBody @Valid UserProfileReqDTO request) {
        Long userId = RequestUserUtil.getCurrentUserId();
        return ResponseEntity.ok(userProfileService.saveProfile(userId, request));
    }

    @PatchMapping("/profile")
    public ResponseEntity<UserProfileResDTO> updateProfile(
            @RequestBody UserProfileReqDTO request) {
        Long userId = RequestUserUtil.getCurrentUserId();
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    @GetMapping("/profile/status")
    public ResponseEntity<ProfileStatusResDTO> getProfileStatus() {
        Long userId = RequestUserUtil.getCurrentUserId();
        return ResponseEntity.ok(userProfileService.getProfileStatus(userId));
    }

    @PostMapping("/profile/generate")
    public ResponseEntity<ProfileGenerationResDTO> generateProfile() {
        Long userId = RequestUserUtil.getCurrentUserId();
        return ResponseEntity.ok(userProfileService.generateProfile(userId));
    }
}
