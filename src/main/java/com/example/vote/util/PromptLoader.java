package com.example.vote.util;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PromptLoader {
    public static String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            return new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }
}
