package com.aimafia.engine;

import com.aimafia.model.Player;

/**
 * Holds the result of night phase resolution.
 */
public record NightResult(
    String mafiaTarget,         // ID of player targeted by Mafia
    String doctorTarget,        // ID of player protected by Doctor
    String sheriffTarget,       // ID of player investigated by Sheriff
    String sheriffResult,       // "MAFIA" or "TOWN" - result of investigation
    boolean killPrevented,      // True if Doctor saved the target
    Player victim               // The player who died, or null if no one died
) {
    /**
     * Creates a NightResult where someone died.
     */
    public static NightResult withDeath(String mafiaTarget, String doctorTarget,
                                         String sheriffTarget, String sheriffResult,
                                         Player victim) {
        return new NightResult(mafiaTarget, doctorTarget, sheriffTarget, 
                sheriffResult, false, victim);
    }

    /**
     * Creates a NightResult where the Doctor saved the target.
     */
    public static NightResult withSave(String mafiaTarget, String doctorTarget,
                                        String sheriffTarget, String sheriffResult) {
        return new NightResult(mafiaTarget, doctorTarget, sheriffTarget, 
                sheriffResult, true, null);
    }

    /**
     * Creates a NightResult where no one was targeted.
     */
    public static NightResult noAction(String sheriffTarget, String sheriffResult) {
        return new NightResult(null, null, sheriffTarget, sheriffResult, false, null);
    }

    /**
     * Checks if someone died during the night.
     *
     * @return true if there was a death
     */
    public boolean hasDeath() {
        return victim != null;
    }

    /**
     * Gets a summary of what happened during the night.
     *
     * @return Summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        
        if (hasDeath()) {
            sb.append(victim.getId()).append(" was killed during the night.");
        } else if (killPrevented) {
            sb.append("The Doctor saved someone from a murder attempt!");
        } else if (mafiaTarget == null) {
            sb.append("The night passed peacefully. No one died.");
        } else {
            sb.append("No one died during the night.");
        }
        
        return sb.toString();
    }

    /**
     * Gets a public-facing summary (no role reveals).
     *
     * @param revealRoles Whether to reveal the victim's role
     * @return Public summary
     */
    public String getPublicSummary(boolean revealRoles) {
        if (hasDeath()) {
            if (revealRoles) {
                return String.format("%s was killed during the night. They were a %s.",
                        victim.getId(), victim.getRole().getDisplayName());
            } else {
                return victim.getId() + " was killed during the night.";
            }
        } else if (killPrevented) {
            return "The sun rises... No one died during the night. Someone was saved!";
        } else {
            return "The sun rises... No one died during the night.";
        }
    }
}
