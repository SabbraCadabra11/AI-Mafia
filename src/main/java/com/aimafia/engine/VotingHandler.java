package com.aimafia.engine;

import com.aimafia.ai.LLMResponse;
import com.aimafia.ai.OpenRouterService;
import com.aimafia.config.GameConfig;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.util.GameLogger;
import com.aimafia.validation.ActionValidator;
import com.aimafia.validation.ActionValidator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Handles the voting/trial phase of the game.
 * Three stages: Nomination (parallel), Defense, Final Judgment (parallel).
 */
public class VotingHandler {
    private static final Logger logger = LoggerFactory.getLogger(VotingHandler.class);
    
    private final OpenRouterService aiService;
    private final ActionValidator validator;
    private final GameLogger gameLogger;
    private final GameConfig config;

    public VotingHandler(OpenRouterService aiService, ActionValidator validator,
                         GameLogger gameLogger) {
        this.aiService = aiService;
        this.validator = validator;
        this.gameLogger = gameLogger;
        this.config = GameConfig.getInstance();
    }

    /**
     * Result of the voting phase.
     */
    public record VotingResult(
        boolean hasExecution,
        Player executed,
        Map<String, Integer> nominationVotes,
        Map<String, String> judgmentVotes
    ) {
        public static VotingResult noExecution(Map<String, Integer> nominations) {
            return new VotingResult(false, null, nominations, Map.of());
        }

        public static VotingResult withExecution(Player executed, 
                                                   Map<String, Integer> nominations,
                                                   Map<String, String> judgments) {
            return new VotingResult(true, executed, nominations, judgments);
        }
    }

    /**
     * Executes the full voting phase.
     *
     * @param state The current game state
     * @return The result of voting
     */
    public VotingResult execute(GameState state) {
        state.setCurrentPhase(Phase.DAY_VOTING);
        gameLogger.logPhaseTransition(state);
        
        logger.info("Starting voting phase for day {}", state.getDayNumber());
        
        // Stage A: Nomination
        Map<String, Integer> nominations = executeNominationPhase(state);
        
        if (nominations.isEmpty()) {
            state.addToPublicLog("No nominations were made. The day ends peacefully.");
            gameLogger.logPublicEvent(state, "No nominations - day ends");
            return VotingResult.noExecution(nominations);
        }
        
        // Find the leader (most nominated)
        String nomineeId = findNominationLeader(nominations, state);
        
        if (nomineeId == null) {
            state.addToPublicLog("SKIP votes won. No trial today.");
            gameLogger.logPublicEvent(state, "SKIP votes won");
            return VotingResult.noExecution(nominations);
        }
        
        Player accused = state.getPlayerById(nomineeId);
        if (accused == null || !accused.isAlive()) {
            logger.error("Invalid nominee: {}", nomineeId);
            return VotingResult.noExecution(nominations);
        }
        
        int nomineeVotes = nominations.getOrDefault(nomineeId, 0);
        int aliveCount = state.getAliveCount();
        int threshold = (aliveCount * config.getNominationThresholdPercent()) / 100;
        
        if (nomineeVotes < threshold) {
            state.addToPublicLog(String.format(
                "%s received only %d votes (needed %d). No trial today.",
                nomineeId, nomineeVotes, threshold));
            gameLogger.logPublicEvent(state, nomineeId + " below threshold");
            return VotingResult.noExecution(nominations);
        }
        
        state.addToPublicLog(String.format(
            "%s has been nominated for trial with %d votes!", nomineeId, nomineeVotes));
        gameLogger.logPublicEvent(state, nomineeId + " nominated for trial");
        
        // Stage B: Defense
        String defenseSpeech = executeDefensePhase(state, accused);
        
        // Stage C: Final Judgment
        Map<String, String> judgments = executeJudgmentPhase(state, accused, defenseSpeech);
        
        // Count votes
        long guiltyVotes = judgments.values().stream()
                .filter("GUILTY"::equalsIgnoreCase).count();
        long innocentVotes = judgments.values().stream()
                .filter("INNOCENT"::equalsIgnoreCase).count();
        
        state.addToPublicLog(String.format(
            "Votes: GUILTY %d - INNOCENT %d", guiltyVotes, innocentVotes));
        
        if (guiltyVotes > innocentVotes) {
            // Execution
            accused.kill();
            
            String deathMessage;
            if (config.isRevealRolesOnDeath()) {
                deathMessage = String.format(
                    "%s has been executed! They were a %s.",
                    accused.getId(), accused.getRole().getDisplayName());
            } else {
                deathMessage = accused.getId() + " has been executed!";
            }
            
            state.addToPublicLog(deathMessage);
            gameLogger.logDeath(accused, "Execution", config.isRevealRolesOnDeath());
            
            return VotingResult.withExecution(accused, nominations, judgments);
        } else {
            state.addToPublicLog(accused.getId() + " has been found INNOCENT and survives.");
            gameLogger.logPublicEvent(state, accused.getId() + " found innocent");
            return VotingResult.noExecution(nominations);
        }
    }

    /**
     * Stage A: Parallel nomination.
     */
    private Map<String, Integer> executeNominationPhase(GameState state) {
        List<Player> voters = state.getAlivePlayers();
        Map<String, Integer> votes = new ConcurrentHashMap<>();
        
        state.addToPublicLog("=== NOMINATION PHASE ===");
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            
            for (Player voter : voters) {
                futures.add(executor.submit(() -> {
                    try {
                        String prompt = aiService.getPromptBuilder()
                                .buildNominationPrompt(voter, state);
                        
                        LLMResponse response = aiService.query(voter, prompt);
                        gameLogger.logPrivateThought(voter, response.thought());
                        
                        String target = response.action();
                        if (target != null && !target.isBlank() && 
                            !"SKIP".equalsIgnoreCase(target)) {
                            
                            ValidationResult validation = 
                                    validator.validateNomination(voter, target, state);
                            
                            if (validation.isValid()) {
                                votes.merge(target, 1, Integer::sum);
                                gameLogger.logAction(voter, "Nominates: " + target);
                            } else {
                                logger.warn("Invalid nomination by {}: {}", 
                                        voter.getId(), validation.errorMessage());
                            }
                        } else {
                            votes.merge("SKIP", 1, Integer::sum);
                            gameLogger.logAction(voter, "Nominates: SKIP");
                        }
                    } catch (Exception e) {
                        logger.error("Nomination failed for {}: {}", 
                                voter.getId(), e.getMessage());
                        votes.merge("SKIP", 1, Integer::sum);
                    }
                }));
            }
            
            // Wait for all nominations
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.error("Nomination timeout: {}", e.getMessage());
                }
            }
        }
        
        // Log nomination results
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (!"SKIP".equals(entry.getKey())) {
                state.addRawToPublicLog(String.format(
                    "%s received %d nomination(s)", entry.getKey(), entry.getValue()));
            }
        }
        
        return new HashMap<>(votes);
    }

    private String findNominationLeader(Map<String, Integer> votes, GameState state) {
        String leader = null;
        int maxVotes = 0;
        
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            String target = entry.getKey();
            int count = entry.getValue();
            
            // Skip the SKIP votes for leader selection
            if ("SKIP".equalsIgnoreCase(target)) continue;
            
            // Verify target is a valid, alive player
            Player player = state.getPlayerById(target);
            if (player != null && player.isAlive() && count > maxVotes) {
                maxVotes = count;
                leader = target;
            }
        }
        
        // Check if SKIP won
        int skipVotes = votes.getOrDefault("SKIP", 0);
        if (skipVotes >= maxVotes) {
            return null;
        }
        
        return leader;
    }

    /**
     * Stage B: Defense speech.
     */
    private String executeDefensePhase(GameState state, Player accused) {
        state.addToPublicLog("=== DEFENSE PHASE ===");
        state.addToPublicLog(accused.getId() + " will now make their defense.");
        
        String prompt = aiService.getPromptBuilder().buildDefensePrompt(accused, state);
        LLMResponse response = aiService.query(accused, prompt);
        
        gameLogger.logPrivateThought(accused, response.thought());
        
        String defenseSpeech = response.message();
        if (defenseSpeech == null || defenseSpeech.isBlank()) {
            defenseSpeech = accused.getId() + " chose to remain silent.";
        } else {
            defenseSpeech = cleanMessage(defenseSpeech);
        }
        
        String formattedDefense = accused.getId() + " (defense): \"" + defenseSpeech + "\"";
        state.addRawToPublicLog(formattedDefense);
        gameLogger.logMessage(accused, "[DEFENSE] " + defenseSpeech);
        
        return defenseSpeech;
    }

    /**
     * Stage C: Parallel final judgment.
     */
    private Map<String, String> executeJudgmentPhase(GameState state, Player accused,
                                                      String defenseSpeech) {
        state.addToPublicLog("=== FINAL JUDGMENT ===");
        state.addToPublicLog("Cast your votes: GUILTY or INNOCENT");
        
        List<Player> voters = state.getAlivePlayers().stream()
                .filter(p -> !p.getId().equals(accused.getId()))
                .toList();
        
        Map<String, String> votes = new ConcurrentHashMap<>();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            
            for (Player voter : voters) {
                futures.add(executor.submit(() -> {
                    try {
                        String prompt = aiService.getPromptBuilder()
                                .buildJudgmentPrompt(voter, accused, defenseSpeech, state);
                        
                        LLMResponse response = aiService.query(voter, prompt);
                        gameLogger.logPrivateThought(voter, response.thought());
                        
                        String vote = response.action();
                        if (response.isGuilty()) {
                            votes.put(voter.getId(), "GUILTY");
                            gameLogger.logAction(voter, "Votes: GUILTY");
                        } else if (response.isInnocent()) {
                            votes.put(voter.getId(), "INNOCENT");
                            gameLogger.logAction(voter, "Votes: INNOCENT");
                        } else {
                            // Default to innocent if invalid
                            votes.put(voter.getId(), "INNOCENT");
                            logger.warn("Invalid judgment vote by {}: {}", voter.getId(), vote);
                        }
                    } catch (Exception e) {
                        logger.error("Judgment failed for {}: {}", voter.getId(), e.getMessage());
                        votes.put(voter.getId(), "INNOCENT");
                    }
                }));
            }
            
            // Wait for all votes
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.error("Judgment timeout: {}", e.getMessage());
                }
            }
        }
        
        return new HashMap<>(votes);
    }

    private String cleanMessage(String message) {
        message = message.trim();
        if (message.startsWith("\"") && message.endsWith("\"")) {
            message = message.substring(1, message.length() - 1);
        }
        message = message.replaceAll("\\s+", " ");
        if (message.length() > 500) {
            message = message.substring(0, 497) + "...";
        }
        return message;
    }
}
