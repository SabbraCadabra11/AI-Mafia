package com.aimafia.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single AI agent playing the Mafia game.
 * Each player has a unique ID, assigned role, status, and maintains
 * their own context memory of game events from their perspective.
 */
public class Player {
    private final String id;
    private String modelId;
    private Role role;
    private Status status;
    private final StringBuilder contextMemory;
    private final Map<String, Object> attributes;

    /**
     * Creates a new player with the given ID.
     * Role must be assigned separately after creation.
     *
     * @param id Unique identifier (e.g., "Player_1")
     */
    public Player(String id) {
        this.id = Objects.requireNonNull(id, "Player ID cannot be null");
        this.status = Status.ALIVE;
        this.contextMemory = new StringBuilder();
        this.attributes = new HashMap<>();
    }

    /**
     * Creates a new player with the given ID and role.
     *
     * @param id   Unique identifier (e.g., "Player_1")
     * @param role The role assigned to this player
     */
    public Player(String id, Role role) {
        this(id);
        this.role = Objects.requireNonNull(role, "Role cannot be null");
    }

    /**
     * Creates a new player with ID, role, and model.
     *
     * @param id      Unique identifier (e.g., "Player_1")
     * @param role    The role assigned to this player
     * @param modelId The LLM model ID for this player
     */
    public Player(String id, Role role, String modelId) {
        this(id, role);
        this.modelId = modelId;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public Role getRole() {
        return role;
    }

    public Status getStatus() {
        return status;
    }

    public String getContextMemory() {
        return contextMemory.toString();
    }

    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    // Setters
    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role, "Role cannot be null");
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "Status cannot be null");
    }

    // Context Memory Management
    /**
     * Appends an event to this player's context memory.
     *
     * @param event The event description to add
     */
    public void addToContext(String event) {
        if (event != null && !event.isBlank()) {
            contextMemory.append(event).append("\n");
        }
    }

    /**
     * Clears the player's context memory.
     */
    public void clearContext() {
        contextMemory.setLength(0);
    }

    // Attribute Management
    /**
     * Sets an attribute flag on this player.
     *
     * @param key   The attribute key
     * @param value The attribute value
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets an attribute value.
     *
     * @param key The attribute key
     * @return The attribute value, or null if not set
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Checks if an attribute is set.
     *
     * @param key The attribute key
     * @return true if the attribute exists
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    // Convenience Methods
    public boolean isAlive() {
        return status == Status.ALIVE;
    }

    public boolean isDead() {
        return status == Status.DEAD;
    }

    public boolean isMafia() {
        return role != null && role.isMafia();
    }

    public boolean isTown() {
        return role != null && role.isTown();
    }

    /**
     * Kills this player, changing their status to DEAD.
     */
    public void kill() {
        this.status = Status.DEAD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Player{id='%s', model='%s', role=%s, status=%s}", id, modelId, role, status);
    }
}
