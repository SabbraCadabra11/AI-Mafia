package com.aimafia.engine;

import com.aimafia.model.GameState;
import com.aimafia.model.Role;

import java.util.Optional;

/**
 * Checks win conditions after each game state change.
 */
public class WinConditionChecker {

    /**
     * Represents the winning team.
     */
    public enum Team {
        MAFIA("The Mafia wins! The Town has been eliminated."),
        TOWN("The Town wins! All Mafia members have been eliminated.");

        private final String victoryMessage;

        Team(String victoryMessage) {
            this.victoryMessage = victoryMessage;
        }

        public String getVictoryMessage() {
            return victoryMessage;
        }
    }

    /**
     * Checks if the game has ended.
     *
     * @param state The current game state
     * @return true if the game is over
     */
    public boolean isGameOver(GameState state) {
        return checkWinner(state).isPresent();
    }

    /**
     * Checks the current win condition.
     *
     * @param state The current game state
     * @return Optional containing the winning team, or empty if game continues
     */
    public Optional<Team> checkWinner(GameState state) {
        int mafiaAlive = state.getAliveMafiaCount();
        int townAlive = state.getAliveTownCount();

        // Mafia wins when they equal or outnumber the Town
        if (mafiaAlive >= townAlive && mafiaAlive > 0) {
            return Optional.of(Team.MAFIA);
        }

        // Town wins when all Mafia are eliminated
        if (mafiaAlive == 0) {
            return Optional.of(Team.TOWN);
        }

        // Game continues
        return Optional.empty();
    }

    /**
     * Provides a detailed status of the current game situation.
     *
     * @param state The current game state
     * @return Status description
     */
    public String getGameStatus(GameState state) {
        int mafiaAlive = state.getAliveMafiaCount();
        int townAlive = state.getAliveTownCount();
        int totalAlive = state.getAliveCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Game Status ===\n");
        sb.append(String.format("Day: %d\n", state.getDayNumber()));
        sb.append(String.format("Phase: %s\n", state.getCurrentPhase().getDisplayName()));
        sb.append(String.format("Alive: %d players\n", totalAlive));
        sb.append(String.format("  - Mafia: %d\n", mafiaAlive));
        sb.append(String.format("  - Town: %d\n", townAlive));
        
        // Danger level assessment
        if (mafiaAlive == townAlive - 1) {
            sb.append("\n⚠️ CRITICAL: Town is one mistake from losing!\n");
        } else if (mafiaAlive == 1) {
            sb.append("\n✓ Only one Mafia remains. Town is close to victory.\n");
        }

        return sb.toString();
    }

    /**
     * Calculates the number of Town members that can die before Mafia wins.
     *
     * @param state The current game state
     * @return Number of "safe" deaths for Town
     */
    public int getTownMargin(GameState state) {
        int mafiaAlive = state.getAliveMafiaCount();
        int townAlive = state.getAliveTownCount();
        // Mafia wins when mafiaAlive >= townAlive
        // So Town can lose (townAlive - mafiaAlive - 1) members
        return Math.max(0, townAlive - mafiaAlive - 1);
    }
}
