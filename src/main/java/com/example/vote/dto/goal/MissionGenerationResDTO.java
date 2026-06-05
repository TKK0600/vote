package com.example.vote.dto.goal;

import java.util.List;

public record MissionGenerationResDTO(
        boolean achievable,
        String note,
        List<MissionResDTO> missions
) {}
