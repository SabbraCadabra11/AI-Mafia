package com.aimafia.model;

/**
 * Represents the alive/dead status of a player.
 */
public enum Status {
    ALIVE("Alive"),
    DEAD("Dead");

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAlive() {
        return this == ALIVE;
    }
}
