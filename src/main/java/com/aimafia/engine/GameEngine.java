package com.aimafia.engine;

import com.aimafia.ai.OpenRouterService;
import com.aimafia.config.GameConfig;
import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.model.Role;
import com.aimafia.util.GameLogger;
import com.aimafia.util.TokenTracker;
import com.aimafia.validation.ActionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main game controller that orchestrates the Mafia game flow.
 * Manages game state and delegates to phase handlers.
 */
public class GameEngine {
    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    private final GameState state;
    private final OpenRouterService aiService;
    private final ActionValidator validator;
    private final GameLogger gameLogger;
    private final GameConfig config;
    private final WinConditionChecker winChecker;
    private final NightPhaseHandler nightHandler;
    private final DayPhaseHandler dayHandler;
    private final VotingHandler votingHandler;

    public GameEngine() {
        this.config = GameConfig.getInstance();
        this.state = new GameState();
        this.aiService = new OpenRouterService();
        this.validator = new ActionValidator();
        this.gameLogger = new GameLogger();
        this.winChecker = new WinConditionChecker();
        this.nightHandler = new NightPhaseHandler(aiService, validator, gameLogger);
        this.dayHandler = new DayPhaseHandler(aiService, gameLogger);
        this.votingHandler = new VotingHandler(aiService, validator, gameLogger);
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    public GameEngine(GameState state, OpenRouterService aiService,
            ActionValidator validator, GameLogger gameLogger) {
        this.config = GameConfig.getInstance();
        this.state = state;
        this.aiService = aiService;
        this.validator = validator;
        this.gameLogger = gameLogger;
        this.winChecker = new WinConditionChecker();
        this.nightHandler = new NightPhaseHandler(aiService, validator, gameLogger);
        this.dayHandler = new DayPhaseHandler(aiService, gameLogger);
        this.votingHandler = new VotingHandler(aiService, validator, gameLogger);
    }

    /**
     * Runs the complete game from start to finish.
     */
    public void run() {
        logger.info("=== AI MAFIA GAME STARTING ===");
        gameLogger.logPublicEvent("Game starting with " + config.getPlayerCount() + " players");

        try {
            // Initialize players with roles
            initializePlayers();

            // Display initial setup
            displayGameSetup();

            // Main game loop
            while (!winChecker.isGameOver(state)) {
                logger.info("\n{}", winChecker.getGameStatus(state));

                // Night Phase
                executeNightPhase();

                // Check win condition after night
                if (winChecker.isGameOver(state)) {
                    break;
                }

                // Day Discussion Phase
                dayHandler.execute(state);

                // Check win condition (in case discussion changes something)
                if (winChecker.isGameOver(state)) {
                    break;
                }

                // Day Voting Phase
                votingHandler.execute(state);

                // Check win condition after voting
                if (winChecker.isGameOver(state)) {
                    break;
                }

                // Advance to next day
                state.incrementDay();
            }

            // Game ended
            displayResults();

        } catch (Exception e) {
            logger.error("Fatal error during game: {}", e.getMessage(), e);
            gameLogger.logError("Game crashed", e);
        }
    }

    /**
     * Initializes players with their roles and models.
     * Role distribution: configurable Mafia, 1 Sheriff, 1 Doctor, remaining
     * Villagers
     * Each player uses a different LLM model from configuration.
     * Supports fixed role assignments via game.mafia.players, game.doctor.player,
     * game.sheriff.player
     */
    private void initializePlayers() {
        logger.info("Initializing {} players with roles and models", config.getPlayerCount());

        // Create role assignments map (player number -> role)
        Map<Integer, Role> roleAssignments = createRoleAssignments();

        for (int i = 1; i <= config.getPlayerCount(); i++) {
            String playerId = "Player_" + i;
            Role role = roleAssignments.get(i);
            String modelId = config.getModelForPlayer(i);

            Player player = new Player(playerId, role, modelId);
            state.addPlayer(player);

            logger.debug("Created {} with role {} using model {}", playerId, role, modelId);
        }

        // Log role distribution (for debugging/analysis)
        gameLogger.logPublicEvent("Players initialized. Let the game begin!");

        logger.info("Mafia: {}", state.getAliveMafia().stream()
                .map(Player::getId).toList());
    }

    /**
     * Creates role assignments for all players.
     * Respects fixed assignments from config, fills remaining randomly.
     */
    private Map<Integer, Role> createRoleAssignments() {
        Map<Integer, Role> assignments = new HashMap<>();
        Set<Integer> assignedPlayers = new HashSet<>();

        // First, handle fixed assignments
        List<Integer> fixedMafia = config.getFixedMafiaPlayers();
        Integer fixedDoctor = config.getFixedDoctorPlayer();
        Integer fixedSheriff = config.getFixedSheriffPlayer();

        // Assign fixed Mafia players
        for (Integer playerNum : fixedMafia) {
            if (playerNum >= 1 && playerNum <= config.getPlayerCount()) {
                assignments.put(playerNum, Role.MAFIA);
                assignedPlayers.add(playerNum);
                logger.debug("Fixed assignment: Player {} -> MAFIA", playerNum);
            }
        }

        // Assign fixed Doctor
        if (fixedDoctor != null && fixedDoctor >= 1 && fixedDoctor <= config.getPlayerCount()
                && !assignedPlayers.contains(fixedDoctor)) {
            assignments.put(fixedDoctor, Role.DOCTOR);
            assignedPlayers.add(fixedDoctor);
            logger.debug("Fixed assignment: Player {} -> DOCTOR", fixedDoctor);
        }

        // Assign fixed Sheriff
        if (fixedSheriff != null && fixedSheriff >= 1 && fixedSheriff <= config.getPlayerCount()
                && !assignedPlayers.contains(fixedSheriff)) {
            assignments.put(fixedSheriff, Role.SHERIFF);
            assignedPlayers.add(fixedSheriff);
            logger.debug("Fixed assignment: Player {} -> SHERIFF", fixedSheriff);
        }

        // Create pool of remaining roles
        List<Role> remainingRoles = new ArrayList<>();

        // Add remaining Mafia roles if needed
        int remainingMafiaCount = config.getMafiaCount() - (int) assignments.values().stream()
                .filter(r -> r == Role.MAFIA).count();
        for (int i = 0; i < remainingMafiaCount; i++) {
            remainingRoles.add(Role.MAFIA);
        }

        // Add Sheriff if not already assigned
        if (fixedSheriff == null || !assignedPlayers.contains(fixedSheriff)
                || assignments.get(fixedSheriff) != Role.SHERIFF) {
            boolean sheriffAssigned = assignments.values().stream().anyMatch(r -> r == Role.SHERIFF);
            if (!sheriffAssigned) {
                remainingRoles.add(Role.SHERIFF);
            }
        }

        // Add Doctor if not already assigned
        if (fixedDoctor == null || !assignedPlayers.contains(fixedDoctor)
                || assignments.get(fixedDoctor) != Role.DOCTOR) {
            boolean doctorAssigned = assignments.values().stream().anyMatch(r -> r == Role.DOCTOR);
            if (!doctorAssigned) {
                remainingRoles.add(Role.DOCTOR);
            }
        }

        // Fill remaining with Villagers
        int unassignedCount = config.getPlayerCount() - assignedPlayers.size();
        int villagerCount = unassignedCount - remainingRoles.size();
        for (int i = 0; i < villagerCount; i++) {
            remainingRoles.add(Role.VILLAGER);
        }

        // Shuffle remaining roles
        Collections.shuffle(remainingRoles);

        // Assign remaining roles to unassigned players
        int roleIndex = 0;
        for (int i = 1; i <= config.getPlayerCount(); i++) {
            if (!assignedPlayers.contains(i)) {
                assignments.put(i, remainingRoles.get(roleIndex++));
            }
        }

        return assignments;
    }

    /**
     * Displays the initial game setup.
     */
    private void displayGameSetup() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==========================================\n");
        sb.append("           AI MAFIA - GAME START\n");
        sb.append("==========================================\n");
        sb.append(String.format("Players: %d\n", config.getPlayerCount()));
        sb.append(String.format("Mafia: %d\n", config.getMafiaCount()));
        sb.append(String.format("Town: %d (1 Sheriff, 1 Doctor, %d Villagers)\n",
                config.getPlayerCount() - config.getMafiaCount(),
                config.getPlayerCount() - config.getMafiaCount() - 2));
        sb.append("\n--- Player Models ---\n");
        for (Player p : state.getPlayers()) {
            sb.append(String.format("  %s: %s\n", p.getId(), p.getModelId()));
        }
        sb.append("==========================================\n");

        logger.info(sb.toString());

        // Log player list to public log
        StringBuilder playerList = new StringBuilder("Players: ");
        for (Player p : state.getPlayers()) {
            playerList.append(p.getId()).append(" ");
        }
        state.addRawToPublicLog(playerList.toString());
    }

    /**
     * Executes the night phase.
     */
    private void executeNightPhase() {
        NightResult result = nightHandler.execute(state);

        // Add result to public log
        String publicSummary = result.getPublicSummary(config.isRevealRolesOnDeath());
        state.addRawToPublicLog("[Night " + state.getDayNumber() + "] " + publicSummary);
        gameLogger.logPublicEvent(state, publicSummary);

        // Update all players' context with night result
        for (Player player : state.getAlivePlayers()) {
            player.addToContext(publicSummary);
        }
    }

    /**
     * Displays the final results of the game.
     */
    private void displayResults() {
        WinConditionChecker.Team winner = winChecker.checkWinner(state)
                .orElse(WinConditionChecker.Team.TOWN);

        StringBuilder sb = new StringBuilder();
        sb.append("\n==========================================\n");
        sb.append("               GAME OVER\n");
        sb.append("==========================================\n");
        sb.append("\n").append(winner.getVictoryMessage()).append("\n\n");
        sb.append(String.format("Game lasted %d days\n\n", state.getDayNumber()));

        sb.append("=== FINAL STANDINGS ===\n");
        for (Player p : state.getPlayers()) {
            String status = p.isAlive() ? "✓ ALIVE" : "✗ DEAD";
            String modelShort = getModelShortName(p.getModelId());
            sb.append(String.format("  %s (%s) - %s [%s]\n",
                    p.getId(), modelShort, p.getRole().getDisplayName(), status));
        }

        sb.append("\n==========================================\n");

        logger.info(sb.toString());
        gameLogger.logGameEnd(winner.name(), state);

        // Display token usage
        TokenTracker.getInstance().logSummary();

        logger.info("Game log saved to: {}", gameLogger.getLogFilePath());
    }

    /**
     * Gets the current game state (for testing).
     *
     * @return The game state
     */
    public GameState getState() {
        return state;
    }

    /**
     * Extracts a short name from a model ID.
     * e.g., "openai/gpt-4o" -> "GPT-4o"
     */
    private String getModelShortName(String modelId) {
        if (modelId == null)
            return "unknown";

        // Get the part after the slash
        int slashIndex = modelId.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < modelId.length() - 1) {
            return modelId.substring(slashIndex + 1);
        }
        return modelId;
    }
}
