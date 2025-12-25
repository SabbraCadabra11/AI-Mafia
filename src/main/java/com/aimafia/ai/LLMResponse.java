package com.aimafia.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object representing the response from an LLM.
 * The LLM must respond with a JSON object containing these fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LLMResponse(
        @JsonProperty("thought") String thought, // Internal reasoning (logged but not publicly visible)

        @JsonProperty("message") String message, // Public statement (only in discussion/defense phases)

        @JsonProperty("action") String action // TARGET_ID, SKIP, GUILTY, or INNOCENT
) {
    /**
     * Creates an empty response (used for fallback scenarios).
     */
    public static LLMResponse empty() {
        return new LLMResponse("", "", "SKIP");
    }

    /**
     * Creates a fallback response with a skip action.
     *
     * @param reason The reason for the fallback
     * @return A fallback LLMResponse
     */
    public static LLMResponse fallback(String reason) {
        return new LLMResponse(reason, "", "SKIP");
    }

    /**
     * Checks if this response has a valid action.
     *
     * @return true if action is not null or empty
     */
    public boolean hasAction() {
        return action != null && !action.isBlank();
    }

    /**
     * Checks if this response has a public message.
     *
     * @return true if message is not null or empty
     */
    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    /**
     * Checks if the action is SKIP.
     *
     * @return true if action equals SKIP
     */
    public boolean isSkip() {
        return "SKIP".equalsIgnoreCase(action);
    }

    /**
     * Checks if the action is a GUILTY vote.
     *
     * @return true if action equals GUILTY
     */
    public boolean isGuilty() {
        return "GUILTY".equalsIgnoreCase(action);
    }

    /**
     * Checks if the action is an INNOCENT vote.
     *
     * @return true if action equals INNOCENT
     */
    public boolean isInnocent() {
        return "INNOCENT".equalsIgnoreCase(action);
    }

    /**
     * Gets the target player ID from the action.
     * Returns null if action is SKIP, GUILTY, or INNOCENT.
     *
     * @return The target player ID, or null
     */
    public String getTargetId() {
        if (action == null || action.isBlank()) {
            return null;
        }
        String upper = action.toUpperCase();
        if (upper.equals("SKIP") || upper.equals("GUILTY") || upper.equals("INNOCENT")) {
            return null;
        }
        return action;
    }

    @Override
    public String toString() {
        return String.format("LLMResponse{action='%s', hasMessage=%s}",
                action, hasMessage());
    }
}
