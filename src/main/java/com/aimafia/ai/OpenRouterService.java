package com.aimafia.ai;

import com.aimafia.config.GameConfig;
import com.aimafia.model.Player;
import com.aimafia.util.TokenTracker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service for communicating with the OpenRouter API.
 * Uses Java's HttpClient with virtual threads for non-blocking I/O.
 */
public class OpenRouterService {
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GameConfig config;
    private final PromptBuilder promptBuilder;
    private final TokenTracker tokenTracker;

    public OpenRouterService() {
        this.config = GameConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
        this.promptBuilder = new PromptBuilder();
        this.tokenTracker = TokenTracker.getInstance();
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    public OpenRouterService(HttpClient httpClient, ObjectMapper objectMapper,
            GameConfig config, TokenTracker tokenTracker) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config;
        this.promptBuilder = new PromptBuilder();
        this.tokenTracker = tokenTracker;
    }

    /**
     * Queries the LLM with a system prompt (from player's role) and user prompt.
     * Uses the player's assigned model.
     *
     * @param player     The player making the query
     * @param userPrompt The user prompt with game context
     * @return The LLM response
     */
    public LLMResponse query(Player player, String userPrompt) {
        String systemPrompt = promptBuilder.buildSystemPrompt(player);
        String modelId = player.getModelId();
        return queryWithRetry(player.getId(), modelId, systemPrompt, userPrompt, config.getMaxRetries());
    }

    /**
     * Queries the LLM with full control over both prompts.
     *
     * @param playerId     The player ID (for logging)
     * @param modelId      The model ID to use
     * @param systemPrompt The system prompt
     * @param userPrompt   The user prompt
     * @return The LLM response
     */
    public LLMResponse queryWithPrompts(String playerId, String modelId, String systemPrompt, String userPrompt) {
        return queryWithRetry(playerId, modelId, systemPrompt, userPrompt, config.getMaxRetries());
    }

    private LLMResponse queryWithRetry(String playerId, String modelId, String systemPrompt,
            String userPrompt, int retriesLeft) {
        try {
            String requestBody = buildRequestBody(modelId, systemPrompt, userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getOpenRouterApiUrl()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getOpenRouterApiKey())
                    .header("HTTP-Referer", "https://github.com/ai-mafia")
                    .header("X-Title", "AI Mafia Game")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            logger.debug("Sending request for {} using model {}", playerId, modelId);

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("API error for {} (model {}): {} - {}",
                        playerId, modelId, response.statusCode(), response.body());
                if (retriesLeft > 0) {
                    Thread.sleep(config.getRetryDelayMs());
                    return queryWithRetry(playerId, modelId, systemPrompt, userPrompt, retriesLeft - 1);
                }
                return LLMResponse.fallback("API error: " + response.statusCode());
            }

            return parseResponse(playerId, modelId, response.body(), systemPrompt, userPrompt, retriesLeft);

        } catch (IOException | InterruptedException e) {
            logger.error("Request failed for {} (model {}): {}", playerId, modelId, e.getMessage());
            if (retriesLeft > 0) {
                try {
                    Thread.sleep(config.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return queryWithRetry(playerId, modelId, systemPrompt, userPrompt, retriesLeft - 1);
            }
            return LLMResponse.fallback("Request failed: " + e.getMessage());
        }
    }

    private String buildRequestBody(String modelId, String systemPrompt, String userPrompt)
            throws JsonProcessingException {
        Map<String, Object> requestMap = Map.of(
                "model", modelId,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.7,
                "max_tokens", 500);
        return objectMapper.writeValueAsString(requestMap);
    }

    private LLMResponse parseResponse(String playerId, String modelId, String responseBody,
            String systemPrompt, String userPrompt,
            int retriesLeft) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Track token usage
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                int inputTokens = usage.get("prompt_tokens").asInt(0);
                int outputTokens = usage.get("completion_tokens").asInt(0);
                tokenTracker.addUsage(inputTokens, outputTokens);
            }

            // Extract content
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                logger.error("No choices in response for {}", playerId);
                return LLMResponse.fallback("No choices in response");
            }

            String content = choices.get(0)
                    .get("message")
                    .get("content")
                    .asText();

            logger.debug("Raw response for {} ({}): {}", playerId, modelId, content);

            // Parse the JSON response from the model
            return parseModelJson(playerId, modelId, content, systemPrompt, userPrompt, retriesLeft);

        } catch (Exception e) {
            logger.error("Failed to parse API response for {}: {}", playerId, e.getMessage());
            if (retriesLeft > 0) {
                return queryWithRetry(playerId, modelId, systemPrompt,
                        promptBuilder.buildErrorCorrectionPrompt(userPrompt, e.getMessage()),
                        retriesLeft - 1);
            }
            return LLMResponse.fallback("Parse error: " + e.getMessage());
        }
    }

    private LLMResponse parseModelJson(String playerId, String modelId, String content,
            String systemPrompt, String userPrompt,
            int retriesLeft) {
        try {
            // Try to extract JSON from content (model might add extra text)
            String jsonContent = extractJson(content);
            LLMResponse response = objectMapper.readValue(jsonContent, LLMResponse.class);

            if (!response.hasAction()) {
                logger.warn("Empty action for {} ({})", playerId, modelId);
                if (retriesLeft > 0) {
                    return queryWithRetry(playerId, modelId, systemPrompt,
                            promptBuilder.buildErrorCorrectionPrompt(userPrompt,
                                    "Action field is empty"),
                            retriesLeft - 1);
                }
                return LLMResponse.fallback("Empty action");
            }

            logger.info("Parsed response for {} ({}): action={}", playerId, modelId, response.action());
            return response;

        } catch (JsonProcessingException e) {
            logger.error("Invalid JSON from {} ({}) - {}", playerId, modelId, e.getMessage());
            if (retriesLeft > 0) {
                return queryWithRetry(playerId, modelId, systemPrompt,
                        promptBuilder.buildErrorCorrectionPrompt(userPrompt,
                                "Invalid JSON format: " + e.getMessage()),
                        retriesLeft - 1);
            }
            return LLMResponse.fallback("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Extracts JSON object from a string that may contain surrounding text.
     */
    private String extractJson(String content) {
        content = content.trim();

        // If already valid JSON, return as-is
        if (content.startsWith("{") && content.endsWith("}")) {
            return content;
        }

        // Try to find JSON object in the content
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        // Return original if no JSON found
        return content;
    }

    /**
     * Gets the prompt builder for external prompt construction.
     *
     * @return The PromptBuilder instance
     */
    public PromptBuilder getPromptBuilder() {
        return promptBuilder;
    }
}
