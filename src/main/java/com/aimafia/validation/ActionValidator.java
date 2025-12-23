package com.aimafia.validation;

import com.aimafia.ai.LLMResponse;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates actions returned by LLMs to ensure they follow game rules.
 */
public class ActionValidator {
    private static final Logger logger = LoggerFactory.getLogger(ActionValidator.class);

    /**
     * Validates an action from a player.
     *
     * @param actor    The player performing the action
     * @param response The LLM response containing the action
     * @param state    The current game state
     * @return Validation result with details
     */
    public ValidationResult validate(Player actor, LLMResponse response, GameState state) {
        if (response == null || !response.hasAction()) {
            return ValidationResult.invalid("No action provided");
        }

        String action = response.action();
        
        // SKIP is always valid
        if (response.isSkip()) {
            return ValidationResult.valid();
        }
        
        // GUILTY/INNOCENT are valid only during judgment phase
        if (response.isGuilty() || response.isInnocent()) {
            if (state.getCurrentPhase() == Phase.DAY_VOTING) {
                return ValidationResult.valid();
            }
            return ValidationResult.invalid("GUILTY/INNOCENT only valid during voting phase");
        }
        
        // Target validation for player IDs
        return validateTarget(actor, action, state);
    }

    /**
     * Validates a target player ID.
     *
     * @param actor    The player performing the action
     * @param targetId The target player ID
     * @param state    The current game state
     * @return Validation result
     */
    public ValidationResult validateTarget(Player actor, String targetId, GameState state) {
        // Check if target exists
        Player target = state.getPlayerById(targetId);
        if (target == null) {
            logger.warn("Invalid target {} by {} - player not found", targetId, actor.getId());
            return ValidationResult.invalid("Player " + targetId + " does not exist");
        }
        
        // Check if target is alive
        if (!target.isAlive()) {
            logger.warn("Invalid target {} by {} - player is dead", targetId, actor.getId());
            return ValidationResult.invalid("Player " + targetId + " is dead");
        }
        
        // Self-targeting rules
        if (targetId.equals(actor.getId())) {
            // Doctor can self-protect
            if (actor.getRole() == Role.DOCTOR) {
                return ValidationResult.valid();
            }
            // Others cannot target themselves
            logger.warn("Invalid self-target by {}", actor.getId());
            return ValidationResult.invalid("Cannot target yourself");
        }
        
        // Role-specific rules
        if (state.getCurrentPhase() == Phase.NIGHT) {
            return validateNightTarget(actor, target);}
        
        return ValidationResult.valid();
    }

    /**
     * Validates night phase targeting rules.
     */
    private ValidationResult validateNightTarget(Player actor, Player target) {
        Role actorRole = actor.getRole();
        
        switch (actorRole) {
            case MAFIA:
                // Mafia cannot kill fellow Mafia
                if (target.isMafia()) {
                    return ValidationResult.invalid("Cannot kill fellow Mafia member");
                }
                break;
            case SHERIFF:
                // Sheriff can investigate anyone alive (already validated)
                break;
            case DOCTOR:
                // Doctor can protect anyone alive (already validated)
                break;
            case VILLAGER:
                // Villager has no night action
                return ValidationResult.invalid("Villagers have no night action");
        }
        
        return ValidationResult.valid();
    }

    /**
     * Validates a nomination target.
     *
     * @param nominator The player making the nomination
     * @param targetId  The target player ID
     * @param state     The current game state
     * @return Validation result
     */
    public ValidationResult validateNomination(Player nominator, String targetId, GameState state) {
        if (targetId == null || targetId.isBlank() || "SKIP".equalsIgnoreCase(targetId)) {
            return ValidationResult.valid(); // Abstaining is valid
        }
        
        // Cannot nominate self
        if (targetId.equals(nominator.getId())) {
            return ValidationResult.invalid("Cannot nominate yourself");
        }
        
        return validateTarget(nominator, targetId, state);
    }

    /**
     * Result of action validation.
     */
    public record ValidationResult(boolean isValid, String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
