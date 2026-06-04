package com.example.vote.dto.goal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalResDTO {
    private Long id;
    private String title;
    private String status;
    private String category;
    private LocalDateTime createdAt;
}