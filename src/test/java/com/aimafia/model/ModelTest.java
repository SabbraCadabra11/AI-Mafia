package com.aimafia.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the data model classes.
 */
class PlayerTest {

    @Test
    void player_createdWithCorrectDefaults() {
        Player player = new Player("Player_1");
        
        assertEquals("Player_1", player.getId());
        assertEquals(Status.ALIVE, player.getStatus());
        assertTrue(player.isAlive());
        assertTrue(player.getContextMemory().isEmpty());
    }

    @Test
    void player_withRole() {
        Player player = new Player("Player_1", Role.MAFIA);
        
        assertEquals(Role.MAFIA, player.getRole());
        assertTrue(player.isMafia());
        assertFalse(player.isTown());
    }

    @Test
    void player_kill() {
        Player player = new Player("Player_1", Role.VILLAGER);
        
        player.kill();
        
        assertTrue(player.isDead());
        assertFalse(player.isAlive());
        assertEquals(Status.DEAD, player.getStatus());
    }

    @Test
    void player_contextMemory() {
        Player player = new Player("Player_1");
        
        player.addToContext("Event 1");
        player.addToContext("Event 2");
        
        String context = player.getContextMemory();
        assertTrue(context.contains("Event 1"));
        assertTrue(context.contains("Event 2"));
    }

    @Test
    void player_attributes() {
        Player player = new Player("Player_1");
        
        player.setAttribute("investigated", true);
        player.setAttribute("notes", "suspicious");
        
        assertTrue(player.hasAttribute("investigated"));
        assertEquals(true, player.getAttribute("investigated"));
        assertEquals("suspicious", player.getAttribute("notes"));
    }

    @Test
    void player_equality() {
        Player player1 = new Player("Player_1", Role.MAFIA);
        Player player2 = new Player("Player_1", Role.VILLAGER);
        Player player3 = new Player("Player_2", Role.MAFIA);
        
        assertEquals(player1, player2);  // Same ID
        assertNotEquals(player1, player3);  // Different ID
    }
}

class GameStateTest {

    @Test
    void gameState_createdWithDefaults() {
        GameState state = new GameState();
        
        assertEquals(1, state.getDayNumber());
        assertEquals(Phase.NIGHT, state.getCurrentPhase());
        assertTrue(state.getPlayers().isEmpty());
        assertTrue(state.getPublicLog().isEmpty());
    }

    @Test
    void gameState_playerManagement() {
        GameState state = new GameState();
        state.addPlayer(new Player("Player_1", Role.MAFIA));
        state.addPlayer(new Player("Player_2", Role.SHERIFF));
        state.addPlayer(new Player("Player_3", Role.VILLAGER));
        
        assertEquals(3, state.getAliveCount());
        assertEquals(1, state.getAliveMafiaCount());
        assertEquals(2, state.getAliveTownCount());
        assertNotNull(state.getPlayerById("Player_1"));
        assertNull(state.getPlayerById("Player_99"));
    }

    @Test
    void gameState_phaseTransitions() {
        GameState state = new GameState();
        
        state.setCurrentPhase(Phase.DAY_DISCUSSION);
        assertEquals(Phase.DAY_DISCUSSION, state.getCurrentPhase());
        
        state.setCurrentPhase(Phase.DAY_VOTING);
        assertEquals(Phase.DAY_VOTING, state.getCurrentPhase());
        
        state.incrementDay();
        assertEquals(2, state.getDayNumber());
    }

    @Test
    void gameState_publicLog() {
        GameState state = new GameState();
        
        state.addToPublicLog("Player_1 was killed");
        state.addRawToPublicLog("Raw event");
        
        assertEquals(2, state.getPublicLog().size());
        assertTrue(state.getPublicLogAsString().contains("Player_1 was killed"));
        assertTrue(state.getPublicLogAsString().contains("Raw event"));
    }

    @Test
    void gameState_getAlivePlayersByRole() {
        GameState state = new GameState();
        state.addPlayer(new Player("Player_1", Role.MAFIA));
        state.addPlayer(new Player("Player_2", Role.MAFIA));
        state.addPlayer(new Player("Player_3", Role.SHERIFF));
        
        assertEquals(2, state.getAlivePlayersByRole(Role.MAFIA).size());
        assertEquals(1, state.getAlivePlayersByRole(Role.SHERIFF).size());
        assertEquals(0, state.getAlivePlayersByRole(Role.DOCTOR).size());
    }
}
