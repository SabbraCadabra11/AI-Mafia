package com.aimafia.validation;

import com.aimafia.ai.LLMResponse;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActionValidator.
 */
class ActionValidatorTest {
    
    private ActionValidator validator;
    private GameState state;

    @BeforeEach
    void setUp() {
        validator = new ActionValidator();
        state = new GameState();
        
        // Setup players
        state.addPlayer(new Player("Player_1", Role.MAFIA));
        state.addPlayer(new Player("Player_2", Role.MAFIA));
        state.addPlayer(new Player("Player_3", Role.SHERIFF));
        state.addPlayer(new Player("Player_4", Role.DOCTOR));
        state.addPlayer(new Player("Player_5", Role.VILLAGER));
    }

    @Test
    void validate_skipIsAlwaysValid() {
        Player actor = state.getPlayerById("Player_1");
        LLMResponse response = new LLMResponse("thinking", "", "SKIP");
        
        ActionValidator.ValidationResult result = validator.validate(actor, response, state);
        
        assertTrue(result.isValid());
    }

    @Test
    void validate_guiltyVoteInVotingPhaseIsValid() {
        state.setCurrentPhase(Phase.DAY_VOTING);
        Player voter = state.getPlayerById("Player_3");
        LLMResponse response = new LLMResponse("thinking", "", "GUILTY");
        
        ActionValidator.ValidationResult result = validator.validate(voter, response, state);
        
        assertTrue(result.isValid());
    }

    @Test
    void validate_guiltyVoteOutsideVotingPhaseIsInvalid() {
        state.setCurrentPhase(Phase.DAY_DISCUSSION);
        Player voter = state.getPlayerById("Player_3");
        LLMResponse response = new LLMResponse("thinking", "", "GUILTY");
        
        ActionValidator.ValidationResult result = validator.validate(voter, response, state);
        
        assertFalse(result.isValid());
    }

    @Test
    void validateTarget_deadPlayerIsInvalid() {
        Player actor = state.getPlayerById("Player_1");
        Player target = state.getPlayerById("Player_5");
        target.kill();
        
        ActionValidator.ValidationResult result = 
                validator.validateTarget(actor, "Player_5", state);
        
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("dead"));
    }

    @Test
    void validateTarget_nonExistentPlayerIsInvalid() {
        Player actor = state.getPlayerById("Player_1");
        
        ActionValidator.ValidationResult result = 
                validator.validateTarget(actor, "Player_99", state);
        
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("does not exist"));
    }

    @Test
    void validateTarget_mafiaCannotTargetFellowMafia() {
        state.setCurrentPhase(Phase.NIGHT);
        Player mafioso = state.getPlayerById("Player_1");
        
        ActionValidator.ValidationResult result = 
                validator.validateTarget(mafioso, "Player_2", state);
        
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("fellow Mafia"));
    }

    @Test
    void validateTarget_doctorCanSelfProtect() {
        state.setCurrentPhase(Phase.NIGHT);
        Player doctor = state.getPlayerById("Player_4");
        
        ActionValidator.ValidationResult result = 
                validator.validateTarget(doctor, "Player_4", state);
        
        assertTrue(result.isValid());
    }

    @Test
    void validateTarget_villagerCannotSelfTarget() {
        state.setCurrentPhase(Phase.NIGHT);
        Player villager = state.getPlayerById("Player_5");
        
        ActionValidator.ValidationResult result = 
                validator.validateTarget(villager, "Player_5", state);
        
        assertFalse(result.isValid());
    }

    @Test
    void validateNomination_skipIsValid() {
        Player nominator = state.getPlayerById("Player_3");
        
        ActionValidator.ValidationResult result = 
                validator.validateNomination(nominator, "SKIP", state);
        
        assertTrue(result.isValid());
    }

    @Test
    void validateNomination_selfNominationIsInvalid() {
        Player nominator = state.getPlayerById("Player_3");
        
        ActionValidator.ValidationResult result = 
                validator.validateNomination(nominator, "Player_3", state);
        
        assertFalse(result.isValid());
    }
}
