package com.aimafia.engine;

import com.aimafia.ai.LLMResponse;
import com.aimafia.ai.OpenRouterService;
import com.aimafia.config.GameConfig;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.model.Role;
import com.aimafia.util.GameLogger;
import com.aimafia.validation.ActionValidator;
import com.aimafia.validation.ActionValidator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Handles the night phase of the game.
 * Mafia operates sequentially (for consensus), while Sheriff and Doctor operate in parallel.
 */
public class NightPhaseHandler {
    private static final Logger logger = LoggerFactory.getLogger(NightPhaseHandler.class);
    
    private final OpenRouterService aiService;
    private final ActionValidator validator;
    private final GameLogger gameLogger;
    private final GameConfig config;

    public NightPhaseHandler(OpenRouterService aiService, ActionValidator validator,
                             GameLogger gameLogger) {
        this.aiService = aiService;
        this.validator = validator;
        this.gameLogger = gameLogger;
        this.config = GameConfig.getInstance();
    }

    /**
     * Executes the night phase and returns the result.
     *
     * @param state The current game state
     * @return The result of the night phase
     */
    public NightResult execute(GameState state) {
        state.setCurrentPhase(Phase.NIGHT);
        gameLogger.logPhaseTransition(state);
        
        logger.info("Starting night phase for day {}", state.getDayNumber());
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Start Sheriff and Doctor actions in parallel
            Future<String> sheriffFuture = executor.submit(() -> executeSheriffAction(state));
            Future<String> doctorFuture = executor.submit(() -> executeDoctorAction(state));
            
            // Execute Mafia consensus (sequential within this thread)
            String mafiaTarget = executeMafiaConsensus(state);
            
            // Wait for Sheriff and Doctor
            String sheriffTarget = null;
            String doctorTarget = null;
            String sheriffResult = null;
            
            try {
                sheriffTarget = sheriffFuture.get(120, TimeUnit.SECONDS);
                if (sheriffTarget != null) {
                    Player investigated = state.getPlayerById(sheriffTarget);
                    if (investigated != null) {
                        sheriffResult = investigated.isMafia() ? "MAFIA" : "TOWN";
                        // Update Sheriff's context memory
                        updateSheriffMemory(state, sheriffTarget, sheriffResult);
                    }
                }
            } catch (Exception e) {
                logger.error("Sheriff action failed: {}", e.getMessage());
            }
            
            try {
                doctorTarget = doctorFuture.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Doctor action failed: {}", e.getMessage());
            }
            
            // Resolve the night
            return resolveNight(state, mafiaTarget, doctorTarget, sheriffTarget, sheriffResult);
        }
    }

    /**
     * Executes the Mafia consensus mechanism.
     * Round 1: Sequential voting with visibility of previous votes.
     * Round 2 (if needed): Tie-breaker round.
     */
    private String executeMafiaConsensus(GameState state) {
        List<Player> aliveMafia = state.getAliveMafia();
        
        if (aliveMafia.isEmpty()) {
            logger.warn("No alive Mafia members");
            return null;
        }
        
        // If only one Mafia, they decide alone
        if (aliveMafia.size() == 1) {
            return getSingleMafiaTarget(aliveMafia.get(0), state);
        }
        
        // Round 1: Sequential voting
        Map<String, Integer> voteCounts = new HashMap<>();
        Map<Player, String> votes = new LinkedHashMap<>();
        StringBuilder discussionHistory = new StringBuilder();
        
        Collections.shuffle(aliveMafia);
        
        for (Player mafioso : aliveMafia) {
            String prompt = aiService.getPromptBuilder()
                    .buildNightActionPrompt(mafioso, state, discussionHistory.toString());
            
            LLMResponse response = aiService.query(mafioso, prompt);
            gameLogger.logPrivateThought(mafioso, response.thought());
            
            String target = response.getTargetId();
            if (target != null) {
                ValidationResult validation = validator.validateTarget(mafioso, target, state);
                if (validation.isValid()) {
                    votes.put(mafioso, target);
                    voteCounts.merge(target, 1, Integer::sum);
                    discussionHistory.append(String.format("%s votes for %s: \"%s\"\n",
                            mafioso.getId(), target, 
                            response.thought() != null ? response.thought() : "No reason given"));
                    gameLogger.logAction(mafioso, "Mafia vote: " + target);
                } else {
                    logger.warn("Invalid Mafia target {} by {}: {}", 
                            target, mafioso.getId(), validation.errorMessage());
                }
            }
        }
        
        // Check for consensus
        String consensusTarget = findConsensusTarget(voteCounts, aliveMafia.size());
        if (consensusTarget != null) {
            logger.info("Mafia reached consensus on target: {}", consensusTarget);
            return consensusTarget;
        }
        
        // Round 2: Tie-breaker
        logger.info("No consensus in round 1, starting tie-breaker");
        return executeMafiaTieBreaker(state, aliveMafia, voteCounts, discussionHistory.toString());
    }

    private String executeMafiaTieBreaker(GameState state, List<Player> aliveMafia,
                                           Map<String, Integer> previousVotes, 
                                           String discussionHistory) {
        Set<String> nominatedTargets = previousVotes.keySet();
        
        Map<String, Integer> newVotes = new HashMap<>();
        
        String tieBreakContext = discussionHistory + 
                "\n=== TIE-BREAKER ROUND ===\n" +
                "You must choose from the previously nominated targets: " + 
                String.join(", ", nominatedTargets);
        
        for (Player mafioso : aliveMafia) {
            String prompt = aiService.getPromptBuilder()
                    .buildNightActionPrompt(mafioso, state, tieBreakContext);
            
            LLMResponse response = aiService.query(mafioso, prompt);
            String target = response.getTargetId();
            
            if (target != null && nominatedTargets.contains(target)) {
                newVotes.merge(target, 1, Integer::sum);
            }
        }
        
        // Find consensus or pick random from tied
        String consensusTarget = findConsensusTarget(newVotes, aliveMafia.size());
        if (consensusTarget != null) {
            return consensusTarget;
        }
        
        // Fallback: random from nominated targets
        List<String> targets = new ArrayList<>(nominatedTargets);
        if (!targets.isEmpty()) {
            Collections.shuffle(targets);
            String randomTarget = targets.get(0);
            logger.info("Mafia failed to reach consensus, randomly selected: {}", randomTarget);
            return randomTarget;
        }
        
        return null;
    }

    private String findConsensusTarget(Map<String, Integer> voteCounts, int mafiaCount) {
        if (voteCounts.isEmpty()) return null;
        
        // Find the target with most votes
        String topTarget = null;
        int topVotes = 0;
        
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > topVotes) {
                topVotes = entry.getValue();
                topTarget = entry.getKey();
            }
        }
        
        // Require majority (>= 2 for 3 mafia, or unanimous for 2)
        int requiredVotes = (mafiaCount == 2) ? 2 : (mafiaCount + 1) / 2 + 1;
        if (topVotes >= requiredVotes) {
            return topTarget;
        }
        
        return null;
    }

    private String getSingleMafiaTarget(Player mafioso, GameState state) {
        String prompt = aiService.getPromptBuilder()
                .buildNightActionPrompt(mafioso, state, "You are the only Mafia member alive.");
        
        LLMResponse response = aiService.query(mafioso, prompt);
        gameLogger.logPrivateThought(mafioso, response.thought());
        
        String target = response.getTargetId();
        if (target != null) {
            ValidationResult validation = validator.validateTarget(mafioso, target, state);
            if (validation.isValid()) {
                gameLogger.logAction(mafioso, "Mafia kill: " + target);
                return target;
            }
        }
        
        // Fallback: pick a random Town member
        List<Player> townMembers = state.getAliveTown();
        if (!townMembers.isEmpty()) {
            Collections.shuffle(townMembers);
            return townMembers.get(0).getId();
        }
        
        return null;
    }

    private String executeSheriffAction(GameState state) {
        List<Player> sheriffs = state.getAlivePlayersByRole(Role.SHERIFF);
        if (sheriffs.isEmpty()) return null;
        
        Player sheriff = sheriffs.get(0);
        
        // Build context with previous investigation results
        String investigations = (String) sheriff.getAttribute("investigations");
        String prompt = aiService.getPromptBuilder()
                .buildNightActionPrompt(sheriff, state, investigations);
        
        LLMResponse response = aiService.query(sheriff, prompt);
        gameLogger.logPrivateThought(sheriff, response.thought());
        
        String target = response.getTargetId();
        if (target != null) {
            ValidationResult validation = validator.validateTarget(sheriff, target, state);
            if (validation.isValid()) {
                gameLogger.logAction(sheriff, "Investigate: " + target);
                return target;
            }
        }
        
        return null;
    }

    private void updateSheriffMemory(GameState state, String target, String result) {
        List<Player> sheriffs = state.getAlivePlayersByRole(Role.SHERIFF);
        if (sheriffs.isEmpty()) return;
        
        Player sheriff = sheriffs.get(0);
        String newResult = String.format("Night %d: %s is %s", 
                state.getDayNumber(), target, result);
        
        String existing = (String) sheriff.getAttribute("investigations");
        if (existing == null) {
            sheriff.setAttribute("investigations", newResult);
        } else {
            sheriff.setAttribute("investigations", existing + "\n" + newResult);
        }
        
        sheriff.addToContext(newResult);
        logger.info("Sheriff investigated {}: {}", target, result);
    }

    private String executeDoctorAction(GameState state) {
        List<Player> doctors = state.getAlivePlayersByRole(Role.DOCTOR);
        if (doctors.isEmpty()) return null;
        
        Player doctor = doctors.get(0);
        
        String prompt = aiService.getPromptBuilder()
                .buildNightActionPrompt(doctor, state, null);
        
        LLMResponse response = aiService.query(doctor, prompt);
        gameLogger.logPrivateThought(doctor, response.thought());
        
        String target = response.getTargetId();
        if (target != null) {
            ValidationResult validation = validator.validateTarget(doctor, target, state);
            if (validation.isValid()) {
                gameLogger.logAction(doctor, "Protect: " + target);
                return target;
            }
        }
        
        return null;
    }

    private NightResult resolveNight(GameState state, String mafiaTarget, 
                                      String doctorTarget, String sheriffTarget, 
                                      String sheriffResult) {
        // Check if Doctor saved the target
        if (mafiaTarget != null && mafiaTarget.equals(doctorTarget)) {
            logger.info("Doctor saved {} from Mafia!", mafiaTarget);
            return NightResult.withSave(mafiaTarget, doctorTarget, sheriffTarget, sheriffResult);
        }
        
        // Kill the target
        if (mafiaTarget != null) {
            Player victim = state.getPlayerById(mafiaTarget);
            if (victim != null && victim.isAlive()) {
                victim.kill();
                logger.info("{} was killed by the Mafia", mafiaTarget);
                return NightResult.withDeath(mafiaTarget, doctorTarget, 
                        sheriffTarget, sheriffResult, victim);
            }
        }
        
        // No death
        return NightResult.noAction(sheriffTarget, sheriffResult);
    }
}
