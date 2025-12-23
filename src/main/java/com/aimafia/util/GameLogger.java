package com.aimafia.util;

import com.aimafia.model.GameState;
import com.aimafia.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Specialized logger for game events.
 * Separates public events (visible to players) from private thoughts (for analysis).
 */
public class GameLogger {
    private static final Logger publicLogger = LoggerFactory.getLogger("com.aimafia.game.public");
    private static final Logger privateLogger = LoggerFactory.getLogger("com.aimafia.game.private");
    private static final Logger gameLogger = LoggerFactory.getLogger("com.aimafia.game");
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final Path logDirectory;
    private final Path gameLogFile;
    private final LocalDateTime gameStartTime;

    /**
     * Creates a new GameLogger with a timestamped log file.
     */
    public GameLogger() {
        this.gameStartTime = LocalDateTime.now();
        this.logDirectory = Path.of("logs");
        this.gameLogFile = logDirectory.resolve(
                "mafia-game-" + TIMESTAMP_FORMAT.format(gameStartTime) + ".log");
        
        initializeLogDirectory();
    }

    /**
     * Creates a GameLogger with a custom log directory.
     *
     * @param logDir The directory for log files
     */
    public GameLogger(Path logDir) {
        this.gameStartTime = LocalDateTime.now();
        this.logDirectory = logDir;
        this.gameLogFile = logDirectory.resolve(
                "mafia-game-" + TIMESTAMP_FORMAT.format(gameStartTime) + ".log");
        
        initializeLogDirectory();
    }

    private void initializeLogDirectory() {
        try {
            Files.createDirectories(logDirectory);
            writeToFile("=== AI MAFIA GAME LOG ===");
            writeToFile("Started: " + gameStartTime);
            writeToFile("==============================\n");
        } catch (IOException e) {
            gameLogger.error("Failed to initialize log directory: {}", e.getMessage());
        }
    }

    /**
     * Logs a public event (visible to all players).
     *
     * @param event The event description
     */
    public void logPublicEvent(String event) {
        publicLogger.info(event);
        writeToFile("[PUBLIC] " + event);
    }

    /**
     * Logs a public event with game state context.
     *
     * @param state The current game state
     * @param event The event description
     */
    public void logPublicEvent(GameState state, String event) {
        String formatted = String.format("[Day %d - %s] %s", 
                state.getDayNumber(), state.getCurrentPhase().getDisplayName(), event);
        publicLogger.info(formatted);
        writeToFile("[PUBLIC] " + formatted);
    }

    /**
     * Logs a player's private thought (for post-game analysis).
     *
     * @param player  The player
     * @param thought The thought content
     */
    public void logPrivateThought(Player player, String thought) {
        String formatted = String.format("[%s (%s)] Thought: %s",
                player.getId(), player.getRole(), thought);
        privateLogger.debug(formatted);
        writeToFile("[PRIVATE] " + formatted);
    }

    /**
     * Logs a player's action.
     *
     * @param player The player
     * @param action The action taken
     */
    public void logAction(Player player, String action) {
        String formatted = String.format("[%s (%s)] Action: %s",
                player.getId(), player.getRole(), action);
        gameLogger.info(formatted);
        writeToFile("[ACTION] " + formatted);
    }

    /**
     * Logs a player's public message.
     *
     * @param player  The player
     * @param message The message content
     */
    public void logMessage(Player player, String message) {
        String formatted = String.format("%s: \"%s\"", player.getId(), message);
        publicLogger.info(formatted);
        writeToFile("[MESSAGE] " + formatted);
    }

    /**
     * Logs a phase transition.
     *
     * @param state The current game state
     */
    public void logPhaseTransition(GameState state) {
        String formatted = String.format("=== %s %d ===",
                state.getCurrentPhase().getDisplayName().toUpperCase(),
                state.getDayNumber());
        gameLogger.info("\n{}", formatted);
        writeToFile("\n" + formatted);
    }

    /**
     * Logs a death event.
     *
     * @param player      The dead player
     * @param cause       The cause of death
     * @param revealRole  Whether to reveal the role
     */
    public void logDeath(Player player, String cause, boolean revealRole) {
        String message;
        if (revealRole) {
            message = String.format("%s has died! They were a %s. (%s)",
                    player.getId(), player.getRole().getDisplayName(), cause);
        } else {
            message = String.format("%s has died! (%s)", player.getId(), cause);
        }
        publicLogger.info(message);
        writeToFile("[DEATH] " + message);
    }

    /**
     * Logs the game result.
     *
     * @param winner  The winning team ("MAFIA" or "TOWN")
     * @param state   The final game state
     */
    public void logGameEnd(String winner, GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================\n");
        sb.append("              GAME OVER\n");
        sb.append("========================================\n");
        sb.append("Winner: ").append(winner).append("\n");
        sb.append("Days played: ").append(state.getDayNumber()).append("\n\n");
        
        sb.append("Final player states:\n");
        for (Player p : state.getPlayers()) {
            sb.append(String.format("  %s - %s (%s)\n",
                    p.getId(), p.getRole().getDisplayName(), p.getStatus()));
        }
        sb.append("========================================\n");
        
        gameLogger.info(sb.toString());
        writeToFile(sb.toString());
    }

    /**
     * Logs an error.
     *
     * @param message The error message
     */
    public void logError(String message) {
        gameLogger.error(message);
        writeToFile("[ERROR] " + message);
    }

    /**
     * Logs an error with exception.
     *
     * @param message The error message
     * @param e       The exception
     */
    public void logError(String message, Exception e) {
        gameLogger.error("{}: {}", message, e.getMessage());
        writeToFile("[ERROR] " + message + ": " + e.getMessage());
    }

    private void writeToFile(String content) {
        try {
            Files.writeString(gameLogFile, content + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            gameLogger.error("Failed to write to log file: {}", e.getMessage());
        }
    }

    /**
     * Gets the path to the game log file.
     *
     * @return The log file path
     */
    public Path getLogFilePath() {
        return gameLogFile;
    }
}
