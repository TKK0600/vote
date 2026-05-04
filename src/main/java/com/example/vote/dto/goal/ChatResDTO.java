package com.example.vote.dto.goal;

public record ChatResDTO(
        String question,       // next AI question, null if interview is done
        boolean interviewDone, // true when all questions answered
        int questionNumber,    // e.g. 2 (so frontend can show "2 of 4")
        int maxQuestions       // always 4
) {}
