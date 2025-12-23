package com.aimafia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Thread-safe singleton that loads and provides game configuration.
 * Configuration is loaded from application.properties file.
 */
public final class GameConfig {
    private static final Logger logger = LoggerFactory.getLogger(GameConfig.class);
    private static final String CONFIG_FILE = "application.properties";

    private static volatile GameConfig instance;

    // OpenRouter API settings
    private final String openRouterApiKey;
    private final String openRouterApiUrl;

    // Per-player model configuration
    private final Map<Integer, String> playerModels;

    // Game settings
    private final int playerCount;
    private final int mafiaCount;
    private final boolean revealRolesOnDeath;
    private final int maxDiscussionRounds;
    private final int nominationThresholdPercent;

    // Retry settings
    private final int maxRetries;
    private final long retryDelayMs;

    private GameConfig() {
        Properties props = loadProperties();

        // OpenRouter API settings
        this.openRouterApiKey = resolveProperty(props, "openrouter.api.key", "");
        this.openRouterApiUrl = props.getProperty("openrouter.api.url",
                "https://openrouter.ai/api/v1/chat/completions");

        // Game settings
        this.playerCount = Integer.parseInt(props.getProperty("game.player.count", "10"));
        this.mafiaCount = Integer.parseInt(props.getProperty("game.mafia.count", "3"));
        this.revealRolesOnDeath = Boolean.parseBoolean(
                props.getProperty("game.reveal.roles.on.death", "true"));
        this.maxDiscussionRounds = Integer.parseInt(
                props.getProperty("game.max.discussion.rounds", "2"));
        this.nominationThresholdPercent = Integer.parseInt(
                props.getProperty("game.nomination.threshold.percent", "30"));

        // Load per-player models
        this.playerModels = loadPlayerModels(props);

        // Retry settings
        this.maxRetries = Integer.parseInt(props.getProperty("api.max.retries", "3"));
        this.retryDelayMs = Long.parseLong(props.getProperty("api.retry.delay.ms", "1000"));

        logger.info("GameConfig loaded: players={}, mafia={}, models configured={}",
                playerCount, mafiaCount, playerModels.size());
    }

    /**
     * Loads per-player model configurations.
     * Format: game.player.{N}.model=model-id
     */
    private Map<Integer, String> loadPlayerModels(Properties props) {
        Map<Integer, String> models = new HashMap<>();

        for (int i = 1; i <= playerCount; i++) {
            String key = "game.player." + i + ".model";
            String modelId = props.getProperty(key);

            if (modelId != null && !modelId.isBlank()) {
                models.put(i, modelId.trim());
                logger.debug("Player {} model: {}", i, modelId);
            }
        }

        return Collections.unmodifiableMap(models);
    }

    /**
     * Gets the singleton instance of GameConfig.
     * Uses double-checked locking for thread safety.
     *
     * @return The GameConfig instance
     */
    public static GameConfig getInstance() {
        if (instance == null) {
            synchronized (GameConfig.class) {
                if (instance == null) {
                    instance = new GameConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Reloads the configuration (for testing purposes).
     */
    public static synchronized void reload() {
        instance = null;
        getInstance();
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                logger.debug("Loaded configuration from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration file: {}", e.getMessage());
        }
        return props;
    }

    /**
     * Resolves a property value, supporting environment variable substitution.
     * Format: ${ENV_VAR_NAME}
     */
    private String resolveProperty(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key, defaultValue);
        if (value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envValue = System.getenv(envVar);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
            logger.warn("Environment variable {} not set for property {}", envVar, key);
            return defaultValue;
        }
        return value;
    }

    // Getters
    public String getOpenRouterApiKey() {
        return openRouterApiKey;
    }

    public String getOpenRouterApiUrl() {
        return openRouterApiUrl;
    }

    /**
     * Gets the model ID for a specific player number.
     *
     * @param playerNumber Player number (1-based)
     * @return The model ID, or null if not configured
     */
    public String getModelForPlayer(int playerNumber) {
        return playerModels.get(playerNumber);
    }

    /**
     * Gets all configured player models.
     *
     * @return Map of player number to model ID
     */
    public Map<Integer, String> getPlayerModels() {
        return playerModels;
    }

    /**
     * Gets a list of all unique models being used.
     *
     * @return List of model IDs
     */
    public List<String> getUniqueModels() {
        return playerModels.values().stream()
                .distinct()
                .sorted()
                .toList();
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getMafiaCount() {
        return mafiaCount;
    }

    public boolean isRevealRolesOnDeath() {
        return revealRolesOnDeath;
    }

    public int getMaxDiscussionRounds() {
        return maxDiscussionRounds;
    }

    public int getNominationThresholdPercent() {
        return nominationThresholdPercent;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    /**
     * Validates that required configuration is present.
     *
     * @return true if configuration is valid
     */
    public boolean isValid() {
        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            logger.error("OpenRouter API key is not configured");
            return false;
        }
        if (playerCount < 5) {
            logger.error("Player count must be at least 5");
            return false;
        }
        if (mafiaCount < 1 || mafiaCount >= playerCount / 2) {
            logger.error("Mafia count must be between 1 and less than half of players");
            return false;
        }
        if (playerModels.size() < playerCount) {
            logger.error("Not all players have models configured. Expected {}, found {}",
                    playerCount, playerModels.size());
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("GameConfig{players=%d, mafia=%d, models=%s, revealRoles=%s}",
                playerCount, mafiaCount, getUniqueModels(), revealRolesOnDeath);
    }
}
