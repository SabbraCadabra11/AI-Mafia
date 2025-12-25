package com.aimafia;

import com.aimafia.config.GameConfig;
import com.aimafia.engine.GameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the AI Mafia Game.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        printBanner();

        // Load and validate configuration
        GameConfig config = GameConfig.getInstance();

        if (!config.isValid()) {
            logger.error("Invalid configuration. Please check your settings.");
            printUsage();
            System.exit(1);
        }

        logger.info("Configuration loaded: {}", config);

        // Check for API key
        if (config.getOpenRouterApiKey() == null || config.getOpenRouterApiKey().isBlank()) {
            logger.error("OpenRouter API key not configured!");
            logger.error("Set the OPENROUTER_API_KEY environment variable or update application.properties");
            System.exit(1);
        }

        // Run the game
        try {
            GameEngine engine = new GameEngine();
            engine.run();
        } catch (Exception e) {
            logger.error("Game failed with error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void printBanner() {
        String banner = """

                 █████╗ ██╗    ███╗   ███╗ █████╗ ███████╗██╗ █████╗
                ██╔══██╗██║    ████╗ ████║██╔══██╗██╔════╝██║██╔══██╗
                ███████║██║    ██╔████╔██║███████║█████╗  ██║███████║
                ██╔══██║██║    ██║╚██╔╝██║██╔══██║██╔══╝  ██║██╔══██║
                ██║  ██║██║    ██║ ╚═╝ ██║██║  ██║██║     ██║██║  ██║
                ╚═╝  ╚═╝╚═╝    ╚═╝     ╚═╝╚═╝  ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝

                        🎭 AI Agents Playing Mafia 🎭
                ═══════════════════════════════════════════════════
                """;
        System.out.println(banner);
    }

    private static void printUsage() {
        String usage = """

                Usage: java -jar ai-mafia.jar

                Revision: 1.0.0

                Environment Variables:
                  OPENROUTER_API_KEY  - Your OpenRouter API key (required)

                Configuration (application.properties):
                  game.player.count            - Number of players (default: 10)
                  game.mafia.count             - Number of Mafia (default: 3)
                  game.max.discussion.rounds   - Discussion rounds per day (default: 2)
                  game.reveal.roles.on.death   - Reveal roles on death (default: true)

                """;
        System.out.println(usage);
    }
}
