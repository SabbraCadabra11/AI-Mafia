package com.aimafia.model;

/**
 * Represents the current phase of the game.
 */
public enum Phase {
    NIGHT("Night", "The town sleeps while special roles perform their actions."),
    DAY_DISCUSSION("Day Discussion", "Players discuss and share information."),
    DAY_VOTING("Day Voting", "Players vote to eliminate a suspected Mafia member.");

    private final String displayName;
    private final String description;

    Phase(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isNight() {
        return this == NIGHT;
    }

    public boolean isDay() {
        return this == DAY_DISCUSSION || this == DAY_VOTING;
    }
}
