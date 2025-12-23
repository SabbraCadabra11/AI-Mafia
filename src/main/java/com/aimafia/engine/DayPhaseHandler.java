package com.aimafia.engine;

import com.aimafia.ai.LLMResponse;
import com.aimafia.ai.OpenRouterService;
import com.aimafia.config.GameConfig;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.util.GameLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the day discussion phase.
 * Players speak sequentially in random order, with each seeing previous statements.
 */
public class DayPhaseHandler {
    private static final Logger logger = LoggerFactory.getLogger(DayPhaseHandler.class);
    
    private final OpenRouterService aiService;
    private final GameLogger gameLogger;
    private final GameConfig config;

    public DayPhaseHandler(OpenRouterService aiService, GameLogger gameLogger) {
        this.aiService = aiService;
        this.gameLogger = gameLogger;
        this.config = GameConfig.getInstance();
    }

    /**
     * Executes the day discussion phase.
     *
     * @param state The current game state
     */
    public void execute(GameState state) {
        state.setCurrentPhase(Phase.DAY_DISCUSSION);
        gameLogger.logPhaseTransition(state);
        
        logger.info("Starting day discussion for day {}", state.getDayNumber());
        
        int rounds = config.getMaxDiscussionRounds();
        
        for (int round = 1; round <= rounds; round++) {
            logger.info("Discussion round {}/{}", round, rounds);
            executeDiscussionRound(state, round);
        }
    }

    /**
     * Executes a single round of discussion.
     */
    private void executeDiscussionRound(GameState state, int roundNumber) {
        List<Player> speakers = state.getShuffledAlivePlayers();
        List<String> roundStatements = new ArrayList<>();
        
        state.addToPublicLog("--- Discussion Round " + roundNumber + " ---");
        
        for (Player speaker : speakers) {
            try {
                String prompt = aiService.getPromptBuilder()
                        .buildDiscussionPrompt(speaker, state, roundStatements);
                
                LLMResponse response = aiService.query(speaker, prompt);
                
                // Log private thought
                if (response.thought() != null && !response.thought().isBlank()) {
                    gameLogger.logPrivateThought(speaker, response.thought());
                }
                
                // Handle public message
                String message = response.message();
                if (message != null && !message.isBlank()) {
                    // Clean up the message (remove quotes if present)
                    message = cleanMessage(message);
                    
                    String formattedStatement = speaker.getId() + ": \"" + message + "\"";
                    roundStatements.add(formattedStatement);
                    state.addRawToPublicLog(formattedStatement);
                    gameLogger.logMessage(speaker, message);
                    
                    // Add to speaker's context so they remember what they said
                    speaker.addToContext("You said: \"" + message + "\"");
                } else {
                    String silentNote = speaker.getId() + " remained silent.";
                    roundStatements.add(silentNote);
                    state.addRawToPublicLog(silentNote);
                    logger.debug("{} chose not to speak", speaker.getId());
                }
                
            } catch (Exception e) {
                logger.error("Error during discussion for {}: {}", speaker.getId(), e.getMessage());
                String errorNote = speaker.getId() + " experienced an error and could not speak.";
                roundStatements.add(errorNote);
            }
        }
    }

    /**
     * Cleans up a message by removing surrounding quotes and normalizing whitespace.
     */
    private String cleanMessage(String message) {
        message = message.trim();
        
        // Remove surrounding quotes
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }
        
        // Normalize whitespace
        message = message.replaceAll("\\s+", " ");
        
        // Limit length
        if (message.length() > 500) {
            message = message.substring(0, 497) + "...";
        }
        
        return message;
    }
}
