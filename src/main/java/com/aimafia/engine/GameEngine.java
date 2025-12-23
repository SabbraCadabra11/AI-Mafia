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
import java.util.List;

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
     * Role distribution: 3 Mafia, 1 Sheriff, 1 Doctor, remaining Villagers
     * Each player uses a different LLM model from configuration.
     */
    private void initializePlayers() {
        logger.info("Initializing {} players with roles and models", config.getPlayerCount());

        List<Role> roles = createRolePool();
        Collections.shuffle(roles);

        for (int i = 1; i <= config.getPlayerCount(); i++) {
            String playerId = "Player_" + i;
            Role role = roles.get(i - 1);
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
     * Creates the pool of roles based on configuration.
     */
    private List<Role> createRolePool() {
        List<Role> roles = new ArrayList<>();

        // Add Mafia roles
        for (int i = 0; i < config.getMafiaCount(); i++) {
            roles.add(Role.MAFIA);
        }

        // Add special town roles
        roles.add(Role.SHERIFF);
        roles.add(Role.DOCTOR);

        // Fill remaining with Villagers
        int villagerCount = config.getPlayerCount() - config.getMafiaCount() - 2;
        for (int i = 0; i < villagerCount; i++) {
            roles.add(Role.VILLAGER);
        }

        return roles;
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
