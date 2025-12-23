package com.aimafia.ai;

import com.aimafia.model.GameState;
import com.aimafia.model.Phase;
import com.aimafia.model.Player;
import com.aimafia.model.Role;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds prompts for LLM queries based on game state and player context.
 */
public class PromptBuilder {

    private static final String JSON_FORMAT_INSTRUCTION = """

            You MUST respond with a valid JSON object in this exact format:
            {
              "thought": "Your internal reasoning (not visible to other players)",
              "message": "Your public statement (at most 5 sentences, only for discussion/defense phases, empty string otherwise)",
              "action": "Your action (TARGET_ID or SKIP or GUILTY or INNOCENT depending on context)"
            }

            IMPORTANT: Respond ONLY with the JSON object. No other text before or after.
            """;

    /**
     * Builds the system prompt for a player based on their role.
     *
     * @param player The player to build the prompt for
     * @return The system prompt
     */
    public String buildSystemPrompt(Player player) {
        Role role = player.getRole();
        StringBuilder sb = new StringBuilder();

        sb.append("You are playing the social deduction game 'Mafia'. ");
        sb.append("Your player ID is: ").append(player.getId()).append("\n\n");

        sb.append("YOUR ROLE: ").append(role.getDisplayName()).append("\n");
        sb.append(role.getDescription()).append("\n\n");

        // Role-specific instructions
        switch (role) {
            case MAFIA -> {
                sb.append("""
                        The goal of the game is to eliminate all Town members and save the Mafia.
                        As Mafia, you must:
                        - Coordinate with other Mafia members at night to kill a Town member
                        - Blend in during day discussions, deflecting suspicion from yourself
                        - Protect fellow Mafia members
                        - Vote to eliminate Town members, especially those close to discovering Mafia

                        You will know who the other Mafia members are.
                        """);
            }
            case SHERIFF -> {
                sb.append("""
                        The goal of the game is to eliminate all Mafia members and save the Town.
                        As Sheriff, you must:
                        - Investigate one player each night to learn if they are Mafia or Town
                        - Use your investigation results wisely during day discussions
                        - Be careful about revealing your role too early (Mafia may target you)
                        - Lead the Town toward eliminating Mafia members
                        """);
            }
            case DOCTOR -> {
                sb.append("""
                        The goal of the game is to eliminate all Mafia members and save the Town.
                        As Doctor, you must:
                        - Protect one player each night from being killed
                        - Try to predict who the Mafia will target
                        - Consider protecting confirmed Town members or likely targets
                        - You may protect yourself, but choose wisely
                        """);
            }
            case VILLAGER -> {
                sb.append("""
                        The goal of the game is to eliminate all Mafia members and save the Town.
                        As Villager, you must:
                        - Participate actively in day discussions
                        - Analyze player behavior to identify Mafia members
                        - Vote based on evidence and logical deduction
                        - Work with other Town members to eliminate all Mafia
                        """);
            }
        }

        sb.append("\n");
        sb.append(JSON_FORMAT_INSTRUCTION);

        return sb.toString();
    }

    /**
     * Builds the user prompt for a night action.
     *
     * @param player    The player making the action
     * @param state     The current game state
     * @param extraInfo Additional context (e.g., Mafia consensus history)
     * @return The user prompt
     */
    public String buildNightActionPrompt(Player player, GameState state, String extraInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== NIGHT ").append(state.getDayNumber()).append(" ===\n\n");

        // Add player's context memory
        if (!player.getContextMemory().isBlank()) {
            sb.append("Your memory of previous events:\n");
            sb.append(player.getContextMemory()).append("\n");
        }

        // List alive players
        sb.append("ALIVE PLAYERS:\n");
        for (Player p : state.getAlivePlayers()) {
            sb.append("- ").append(p.getId());
            if (p.getId().equals(player.getId())) {
                sb.append(" (YOU)");
            }
            sb.append("\n");
        }
        sb.append("\n");

        // Role-specific context
        Role role = player.getRole();
        switch (role) {
            case MAFIA -> {
                sb.append("Your fellow Mafia members:\n");
                for (Player m : state.getAliveMafia()) {
                    if (!m.getId().equals(player.getId())) {
                        sb.append("- ").append(m.getId()).append("\n");
                    }
                }
                sb.append("\n");

                if (extraInfo != null && !extraInfo.isBlank()) {
                    sb.append("MAFIA DISCUSSION:\n").append(extraInfo).append("\n");
                }

                sb.append("""
                        Choose a Town member to kill tonight.
                        Your action should be the Player ID of your target.
                        You must coordinate with other Mafia to reach consensus.
                        """);
            }
            case SHERIFF -> {
                if (extraInfo != null && !extraInfo.isBlank()) {
                    sb.append("Your previous investigation results:\n");
                    sb.append(extraInfo).append("\n\n");
                }
                sb.append("""
                        Choose a player to investigate tonight.
                        Your action should be the Player ID you want to investigate.
                        You will learn if they are MAFIA or TOWN.
                        """);
            }
            case DOCTOR -> {
                sb.append("""
                        Choose a player to protect tonight.
                        Your action should be the Player ID you want to save.
                        If the Mafia targets this player, they will survive.
                        """);
            }
            default -> {
                sb.append("You have no night action. Waiting for dawn...\n");
                sb.append("Set your action to 'SKIP'.\n");
            }
        }

        return sb.toString();
    }

    /**
     * Builds the user prompt for day discussion.
     *
     * @param player             The player making a statement
     * @param state              The current game state
     * @param previousStatements Statements made so far in this round
     * @return The user prompt
     */
    public String buildDiscussionPrompt(Player player, GameState state, List<String> previousStatements) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DAY ").append(state.getDayNumber()).append(" - DISCUSSION ===\n\n");

        // Full game history
        List<String> gameLog = state.getPublicLog();
        if (!gameLog.isEmpty()) {
            sb.append("GAME HISTORY:\n");
            for (String event : gameLog) {
                sb.append(event).append("\n");
            }
            sb.append("\n");
        }

        // Previous statements in this round
        if (previousStatements != null && !previousStatements.isEmpty()) {
            sb.append("Discussion so far:\n");
            for (String statement : previousStatements) {
                sb.append(statement).append("\n");
            }
            sb.append("\n");
        }

        // Alive players
        sb.append("ALIVE PLAYERS: ");
        sb.append(state.getAlivePlayers().stream()
                .map(Player::getId)
                .collect(Collectors.joining(", ")));
        sb.append("\n\n");

        // Player's context
        if (!player.getContextMemory().isBlank()) {
            sb.append("Your private notes:\n");
            sb.append(player.getContextMemory()).append("\n");
        }

        sb.append("""
                It's your turn to speak. Share your thoughts, suspicions, or defend yourself.
                Be strategic - reveal information carefully and observe how others react.

                Your 'message' will be visible to everyone.
                Your 'action' should be 'SKIP' for the discussion phase.
                """);

        return sb.toString();
    }

    /**
     * Builds the user prompt for nomination voting.
     *
     * @param player The player voting
     * @param state  The current game state
     * @return The user prompt
     */
    public String buildNominationPrompt(Player player, GameState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DAY ").append(state.getDayNumber()).append(" - NOMINATION ===\n\n");

        // Full game history
        sb.append("GAME HISTORY:\n");
        for (String event : state.getPublicLog()) {
            sb.append(event).append("\n");
        }
        sb.append("\n");

        // Alive players (excluding self)
        sb.append("You can nominate one of these players for elimination:\n");
        for (Player p : state.getAlivePlayers()) {
            if (!p.getId().equals(player.getId())) {
                sb.append("- ").append(p.getId()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("""
                Who do you want to nominate for elimination?
                Your 'action' should be the Player ID you want to nominate, or 'SKIP' to abstain.
                """);

        return sb.toString();
    }

    /**
     * Builds the user prompt for the defense speech.
     *
     * @param accused The accused player
     * @param state   The current game state
     * @return The user prompt
     */
    public String buildDefensePrompt(Player accused, GameState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DAY ").append(state.getDayNumber()).append(" - DEFENSE ===\n\n");

        sb.append("You have been nominated for elimination!\n\n");

        sb.append("GAME HISTORY:\n");
        for (String event : state.getPublicLog()) {
            sb.append(event).append("\n");
        }
        sb.append("\n");

        sb.append("""
                This is your chance to defend yourself and convince the Town of your innocence.
                Make a compelling argument. Your life depends on it!

                Your 'message' will be your defense speech, visible to everyone.
                Your 'action' should be 'SKIP'.
                """);

        return sb.toString();
    }

    /**
     * Builds the user prompt for the final judgment vote.
     *
     * @param voter         The player voting
     * @param accused       The accused player
     * @param defenseSpeech The accused's defense speech
     * @param state         The current game state
     * @return The user prompt
     */
    public String buildJudgmentPrompt(Player voter, Player accused, String defenseSpeech, GameState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DAY ").append(state.getDayNumber()).append(" - FINAL JUDGMENT ===\n\n");

        sb.append("The accused: ").append(accused.getId()).append("\n\n");

        sb.append("Their defense:\n\"").append(defenseSpeech).append("\"\n\n");

        // Voter's private knowledge
        if (!voter.getContextMemory().isBlank()) {
            sb.append("Your private knowledge:\n");
            sb.append(voter.getContextMemory()).append("\n");
        }

        sb.append("""
                Cast your vote:
                - GUILTY: You believe they are Mafia and should be eliminated
                - INNOCENT: You believe they are Town and should be spared

                Your 'action' should be either 'GUILTY' or 'INNOCENT'.
                """);

        return sb.toString();
    }

    /**
     * Builds an error correction prompt for invalid responses.
     *
     * @param originalPrompt The original user prompt
     * @param error          The error message
     * @return The corrected prompt
     */
    public String buildErrorCorrectionPrompt(String originalPrompt, String error) {
        return originalPrompt + "\n\n" +
                "ERROR: Your previous response was invalid.\n" +
                "Reason: " + error + "\n\n" +
                "Please provide a valid JSON response.";
    }
}
