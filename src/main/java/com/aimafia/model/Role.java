package com.aimafia.model;

/**
 * Represents the possible roles a player can have in the Mafia game.
 */
public enum Role {
    MAFIA("Mafia", "Your goal is to eliminate all Town members. At night, you coordinate with other Mafia to kill one player.", true),
    SHERIFF("Sheriff", "You are a Town investigator. Each night, you can investigate one player to learn if they are Mafia or Town.", false),
    DOCTOR("Doctor", "You are a Town protector. Each night, you can protect one player from being killed.", false),
    VILLAGER("Villager", "You are a regular Town member. Use logic and discussion to identify and eliminate the Mafia.", false);

    private final String displayName;
    private final String description;
    private final boolean isMafia;

    Role(String displayName, String description, boolean isMafia) {
        this.displayName = displayName;
        this.description = description;
        this.isMafia = isMafia;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isMafia() {
        return isMafia;
    }

    public boolean isTown() {
        return !isMafia;
    }

    /**
     * Returns true if this role has a night action.
     */
    public boolean hasNightAction() {
        return this == MAFIA || this == SHERIFF || this == DOCTOR;
    }
}
