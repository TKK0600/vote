package com.example.vote.dto.profile;

import java.time.LocalDateTime;

public record ProfileGenerationResDTO(
    boolean success,
    String profileMarkdown,
    LocalDateTime generatedAt
) {}
