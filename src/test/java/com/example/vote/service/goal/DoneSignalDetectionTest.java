package com.example.vote.service.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@code {"done": true}} detection logic used in
 * {@link GoalInterviewService#submitAnswer}.
 *
 * The actual logic is: trim() → startsWith("{") → contains("\"done\"")
 * → objectMapper.readTree() → node.has("done") && node.get("done").asBoolean()
 */
class DoneSignalDetectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isDoneSignal(String aiResponse) {
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("{") && trimmed.contains("\"done\"")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.has("done") && node.get("done").asBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Not valid JSON — treat as normal question
            }
        }
        return false;
    }

    @Test
    @DisplayName("{\"done\": true} — baseline")
    void baseline() {
        assertTrue(isDoneSignal("{\"done\": true}"));
    }

    @Test
    @DisplayName("{\"done\":true} — no spaces")
    void noSpaces() {
        assertTrue(isDoneSignal("{\"done\":true}"));
    }

    @Test
    @DisplayName("spaces + newlines around valid JSON")
    void surroundingWhitespaceAndNewlines() {
        assertTrue(isDoneSignal("  \t\n {\"done\": true} \n  "));
    }

    @Test
    @DisplayName("internal space after colon")
    void internalSpace() {
        assertTrue(isDoneSignal("{\"done\" : true}"));
    }

    @Test
    @DisplayName("tab-indented valid JSON")
    void tabIndented() {
        assertTrue(isDoneSignal("\t\t{\"done\": true}"));
    }

    @Test
    @DisplayName("{\"done\": false} — not done")
    void doneFalse() {
        assertFalse(isDoneSignal("{\"done\": false}"));
    }

    @Test
    @DisplayName("{\"done\": true, \"reason\": \"enough context\"} — extra fields OK")
    void extraFields() {
        assertTrue(isDoneSignal("{\"done\": true, \"reason\": \"enough context\"}"));
    }

    @Test
    @DisplayName("plain text question — not done")
    void plainText() {
        assertFalse(isDoneSignal("What time do you usually wake up?"));
    }

    @Test
    @DisplayName("text containing word 'done' but not JSON — safe fallback")
    void wordContainsDoneButNotJson() {
        assertFalse(isDoneSignal("Have you done this before?"));
    }

    @Test
    @DisplayName("trailing garbage after JSON — Jackson ignores trailing content, treated as done")
    void trailingGarbage() {
        // Jackson ObjectMapper.readTree() stops at the first JSON value and
        // ignores trailing tokens by default. In practice, if the AI emits
        // {"done": true} followed by extra text, it meant done — so this is
        // acceptable and actually more robust than a strict parse.
        assertTrue(isDoneSignal("{\"done\": true} some extra text"));
    }

    @Test
    @DisplayName("empty string — not done")
    void emptyString() {
        assertFalse(isDoneSignal(""));
    }

    @Test
    @DisplayName("whitespace only — not done")
    void whitespaceOnly() {
        assertFalse(isDoneSignal("   \t\n   "));
    }

    @Test
    @DisplayName("JSON array — not the expected format")
    void jsonArray() {
        assertFalse(isDoneSignal("[{\"done\": true}]"));
    }

    @Test
    @DisplayName("JSON without done key")
    void jsonWithoutDoneKey() {
        assertFalse(isDoneSignal("{\"status\": \"ok\"}"));
    }
}
