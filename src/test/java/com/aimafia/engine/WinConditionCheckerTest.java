package com.aimafia.engine;

import com.aimafia.model.GameState;
import com.aimafia.model.Player;
import com.aimafia.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WinConditionChecker.
 */
class WinConditionCheckerTest {
    
    private WinConditionChecker checker;
    private GameState state;

    @BeforeEach
    void setUp() {
        checker = new WinConditionChecker();
        state = new GameState();
    }

    @Test
    void townWins_whenAllMafiaAreDead() {
        // Setup: 3 Town alive, 0 Mafia alive
        state.addPlayer(new Player("Player_1", Role.SHERIFF));
        state.addPlayer(new Player("Player_2", Role.DOCTOR));
        state.addPlayer(new Player("Player_3", Role.VILLAGER));
        
        Player deadMafia = new Player("Player_4", Role.MAFIA);
        deadMafia.kill();
        state.addPlayer(deadMafia);
        
        Optional<WinConditionChecker.Team> winner = checker.checkWinner(state);
        
        assertTrue(winner.isPresent());
        assertEquals(WinConditionChecker.Team.TOWN, winner.get());
    }

    @Test
    void mafiaWins_whenMafiaEqualsTown() {
        // Setup: 2 Town alive, 2 Mafia alive
        state.addPlayer(new Player("Player_1", Role.VILLAGER));
        state.addPlayer(new Player("Player_2", Role.VILLAGER));
        state.addPlayer(new Player("Player_3", Role.MAFIA));
        state.addPlayer(new Player("Player_4", Role.MAFIA));
        
        Optional<WinConditionChecker.Team> winner = checker.checkWinner(state);
        
        assertTrue(winner.isPresent());
        assertEquals(WinConditionChecker.Team.MAFIA, winner.get());
    }

    @Test
    void mafiaWins_whenMafiaOutnumbersTown() {
        // Setup: 1 Town alive, 2 Mafia alive
        state.addPlayer(new Player("Player_1", Role.VILLAGER));
        state.addPlayer(new Player("Player_2", Role.MAFIA));
        state.addPlayer(new Player("Player_3", Role.MAFIA));
        
        Optional<WinConditionChecker.Team> winner = checker.checkWinner(state);
        
        assertTrue(winner.isPresent());
        assertEquals(WinConditionChecker.Team.MAFIA, winner.get());
    }

    @Test
    void gameContinues_whenTownOutnumbersMafia() {
        // Setup: 5 Town alive, 2 Mafia alive
        state.addPlayer(new Player("Player_1", Role.SHERIFF));
        state.addPlayer(new Player("Player_2", Role.DOCTOR));
        state.addPlayer(new Player("Player_3", Role.VILLAGER));
        state.addPlayer(new Player("Player_4", Role.VILLAGER));
        state.addPlayer(new Player("Player_5", Role.VILLAGER));
        state.addPlayer(new Player("Player_6", Role.MAFIA));
        state.addPlayer(new Player("Player_7", Role.MAFIA));
        
        Optional<WinConditionChecker.Team> winner = checker.checkWinner(state);
        
        assertFalse(winner.isPresent());
        assertFalse(checker.isGameOver(state));
    }

    @Test
    void getTownMargin_calculatesCorrectly() {
        // Setup: 5 Town, 2 Mafia
        // Town can afford to lose (5 - 2 - 1) = 2 members
        state.addPlayer(new Player("Player_1", Role.SHERIFF));
        state.addPlayer(new Player("Player_2", Role.DOCTOR));
        state.addPlayer(new Player("Player_3", Role.VILLAGER));
        state.addPlayer(new Player("Player_4", Role.VILLAGER));
        state.addPlayer(new Player("Player_5", Role.VILLAGER));
        state.addPlayer(new Player("Player_6", Role.MAFIA));
        state.addPlayer(new Player("Player_7", Role.MAFIA));
        
        assertEquals(2, checker.getTownMargin(state));
    }
}
