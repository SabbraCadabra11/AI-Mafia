package com.aimafia.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stores the global state of the Mafia game.
 * This class maintains the current day, phase, all players, and the public log
 * of events visible to everyone.
 */
public class GameState {
    private int dayNumber;
    private Phase currentPhase;
    private final List<Player> players;
    private final List<String> publicLog;

    /**
     * Creates a new game state with empty player list.
     */
    public GameState() {
        this.dayNumber = 1;
        this.currentPhase = Phase.NIGHT;
        this.players = new ArrayList<>();
        this.publicLog = new ArrayList<>();
    }

    /**
     * Creates a new game state with the given players.
     *
     * @param players List of players in the game
     */
    public GameState(List<Player> players) {
        this();
        this.players.addAll(Objects.requireNonNull(players, "Players list cannot be null"));
    }

    // Getters
    public int getDayNumber() {
        return dayNumber;
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public List<String> getPublicLog() {
        return Collections.unmodifiableList(publicLog);
    }

    // Setters
    public void setCurrentPhase(Phase phase) {
        this.currentPhase = Objects.requireNonNull(phase, "Phase cannot be null");
    }

    /**
     * Increments the day counter.
     */
    public void incrementDay() {
        this.dayNumber++;
    }

    // Player Management
    /**
     * Adds a player to the game.
     *
     * @param player The player to add
     */
    public void addPlayer(Player player) {
        players.add(Objects.requireNonNull(player, "Player cannot be null"));
    }

    /**
     * Gets a player by their ID.
     *
     * @param id The player ID to find
     * @return The player, or null if not found
     */
    public Player getPlayerById(String id) {
        return players.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all alive players.
     *
     * @return List of alive players
     */
    public List<Player> getAlivePlayers() {
        return players.stream()
                .filter(Player::isAlive)
                .collect(Collectors.toList());
    }

    /**
     * Gets all alive players with a specific role.
     *
     * @param role The role to filter by
     * @return List of alive players with that role
     */
    public List<Player> getAlivePlayersByRole(Role role) {
        return players.stream()
                .filter(Player::isAlive)
                .filter(p -> p.getRole() == role)
                .collect(Collectors.toList());
    }

    /**
     * Gets all alive Mafia members.
     *
     * @return List of alive Mafia players
     */
    public List<Player> getAliveMafia() {
        return players.stream()
                .filter(Player::isAlive)
                .filter(Player::isMafia)
                .collect(Collectors.toList());
    }

    /**
     * Gets all alive Town members.
     *
     * @return List of alive Town players
     */
    public List<Player> getAliveTown() {
        return players.stream()
                .filter(Player::isAlive)
                .filter(Player::isTown)
                .collect(Collectors.toList());
    }

    /**
     * Gets a shuffled list of alive players for random ordering.
     *
     * @return Shuffled list of alive players
     */
    public List<Player> getShuffledAlivePlayers() {
        List<Player> shuffled = new ArrayList<>(getAlivePlayers());
        Collections.shuffle(shuffled);
        return shuffled;
    }

    // Public Log Management
    /**
     * Adds an event to the public log.
     *
     * @param event The event description
     */
    public void addToPublicLog(String event) {
        if (event != null && !event.isBlank()) {
            String formattedEvent = String.format("[Day %d - %s] %s",
                    dayNumber, currentPhase.getDisplayName(), event);
            publicLog.add(formattedEvent);
        }
    }

    /**
     * Adds an event to the public log without phase formatting.
     *
     * @param event The raw event description
     */
    public void addRawToPublicLog(String event) {
        if (event != null && !event.isBlank()) {
            publicLog.add(event);
        }
    }

    /**
     * Gets the public log as a single formatted string.
     *
     * @return The full public log
     */
    public String getPublicLogAsString() {
        return String.join("\n", publicLog);
    }

    /**
     * Gets recent entries from the public log.
     *
     * @param count Maximum number of recent entries to return
     * @return List of recent log entries
     */
    public List<String> getRecentPublicLog(int count) {
        int size = publicLog.size();
        if (count >= size) {
            return new ArrayList<>(publicLog);
        }
        return new ArrayList<>(publicLog.subList(size - count, size));
    }

    // Count Methods
    /**
     * Counts alive players.
     *
     * @return Number of alive players
     */
    public int getAliveCount() {
        return (int) players.stream().filter(Player::isAlive).count();
    }

    /**
     * Counts alive Mafia members.
     *
     * @return Number of alive Mafia
     */
    public int getAliveMafiaCount() {
        return (int) players.stream().filter(Player::isAlive).filter(Player::isMafia).count();
    }

    /**
     * Counts alive Town members.
     *
     * @return Number of alive Town members
     */
    public int getAliveTownCount() {
        return (int) players.stream().filter(Player::isAlive).filter(Player::isTown).count();
    }

    @Override
    public String toString() {
        return String.format("GameState{day=%d, phase=%s, alive=%d/%d}",
                dayNumber, currentPhase, getAliveCount(), players.size());
    }
}
