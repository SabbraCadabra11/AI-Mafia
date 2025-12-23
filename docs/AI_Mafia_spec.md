# **Technical Specification: Java AI Mafia Game Engine**

## **1\. System Overview**

This document defines the architecture and business logic for the "Mafia" game engine, designed to facilitate gameplay between 10 autonomous AI agents (Large Language Models). The system operates as a console application (CLI), communicating with language models via the OpenRouter API.

### **1.1. Design Goals**

* **Autonomy:** Complete automation of gameplay with no human intervention.  
* **Concurrency:** Utilization of Virtual Threads (Java 21 Project Loom) to handle I/O operations.  
* **Logic Determinism:** Strict rules for action verification and conflict resolution.

## **2\. Technology Stack**

* **Language:** Java 21 LTS.  
* **Concurrency:** Virtual Threads (java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor).  
* **HTTP Communication:** java.net.http.HttpClient.  
* **Data Format / Serialization:** JSON (Jackson library com.fasterxml.jackson.core).  
* **Logging:** SLF4J \+ Logback (console output and game history file).  
* **Configuration:** properties or YAML file (loaded at startup).

## **3\. Data Model (Data Layer)**

### **3.1. Player Class**

Represents a single AI agent.

* String id: Unique identifier (e.g., "Player\_1").  
* Role role: Enum (MAFIA, SHERIFF, DOCTOR, VILLAGER).  
* Status status: Enum (ALIVE, DEAD).  
* StringBuilder contextMemory: Buffer storing game history as seen through the eyes of this specific player.  
* Map\<String, Object\> attributes: Additional flags (e.g., whether investigated by the Sheriff).

### **3.2. GameState Class**

Stores the global state of the game.

* int dayNumber: Day counter.  
* Phase currentPhase: Enum (NIGHT, DAY\_DISCUSSION, DAY\_VOTING).  
* List\<Player\> players: List of all participants.  
* List\<String\> publicLog: Register of public events (visible to everyone).

### **3.3. Configuration (GameConfig)**

Singleton loading settings from an external file:

* openRouterApiKey: API Key.  
* modelId: Model identifier (e.g., openai/gpt-4o or anthropic/claude-3.5-sonnet).  
* revealRolesOnDeath: boolean – determines if a player's role is revealed in the publicLog upon death.  
* maxDiscussionRounds: int (default 2).

## **4\. AI Integration Architecture (OpenRouterService)**

### **4.1. Communication**

The service uses an HTTP client in synchronous mode, running within virtual threads. This maintains sequential code readability without blocking OS threads while waiting for API responses.

### **4.2. Prompt Structure**

Each request to the LLM consists of:

1. **System Prompt:** Definition of role, goals, and output format.  
   * *Example:* "You are a player in the game of Mafia. Your role is \[ROLE\]. Response format: JSON."  
2. **User Prompt:** Current game state, chat history, result of the previous night's action (only for functional roles).

### **4.3. Response Format (JSON Schema)**

Required response format from the model:

{  
  "thought": "Internal thought process, situation analysis...",  
  "message": "Public statement (only in discussion/defense phase)",  
  "action": "TARGET\_ID or SKIP or GUILTY/INNOCENT"  
}

Responses failing to meet the schema are rejected, and the request is retried (max 3 times). After 3 failed attempts, a FallbackAction (e.g., SKIP) is triggered.

## **5\. Gameplay Loop (GameEngine)**

The main loop is controlled by the GameEngine class.

### **5.1. Night Phase**

In this phase, actions are processed in a hybrid mode: Mafia operates sequentially (to reach consensus), while the Sheriff and Doctor operate in parallel to the Mafia.

#### **5.1.1. Mafia Logic (Sequential Consensus)**

Process for Mafia selecting a victim:

1. **Round 1 (Sequential):**  
   * Randomize Mafia order.  
   * Mafioso 1 selects a target and provides reasoning.  
   * Mafioso 2 receives M1's choice. Selects a target.  
   * Mafioso 3 receives the history (M1, M2). Selects a target.  
2. **Verification:** Check if a target has \>= 2 votes (with 3 alive) or unanimity (with 2 alive).  
3. **Round 2 (Tie-Breaker \- if no consensus):**  
   * Mafiosi are queried again, seeing the vote divergence. They must aim to agree on a common target from the pool nominated in Round 1\.  
4. **Fallback:** If no consensus after R2 \-\> random selection from R2 targets.

#### **5.1.2. Sheriff and Doctor (Parallel Execution)**

* Run on separate virtual threads parallel to Mafia operations.  
* **Sheriff:** Selects a player to investigate. The engine returns "MAFIA" or "TOWN" in the next day's prompt.  
* **Doctor:** Selects a player to heal.

#### **5.1.3. Night Resolution**

* Compare Mafia target with Doctor target.  
* Update victim status (DEAD, if not healed).  
* Generate entry for publicLog (e.g., "Player X died last night" or "No one died").

### **5.2. Day Discussion Phase**

Executed sequentially in random order to ensure conversation continuity.

* Number of rounds: configurable (default 2).  
* In each turn, the list of alive players is shuffled (Collections.shuffle).  
* The player receives the updated publicLog with statements from predecessors in the current turn.  
* The generated statement is immediately added to the public log.

### **5.3. Voting / The Trial Phase**

A three-stage process.

#### **Stage A: Nomination (Parallel Nomination)**

* All alive players are queried in parallel (Virtual Threads).  
* Question: "Who do you nominate for elimination? (ID or SKIP)".  
* **Verification:** Determine the player with the most votes (Leader).  
* **Threshold:** If the Leader did not receive min. X% of votes (e.g., 30%) or the SKIP option won \-\> Day ends with no execution. Otherwise \-\> Stage B.

#### **Stage B: Defense Stand**

* Only the accused (Nomination Leader) is queried.  
* Prompt: "You have been accused. Make your defense speech."  
* The content of the speech is added to publicLog.

#### **Stage C: Final Judgment**

* All alive players (excluding the accused \- forced observer status or auto-innocent) are queried in parallel.  
* Prompt includes the defense speech.  
* Action: JSON {"vote": "GUILTY"} or {"vote": "INNOCENT"}.  
* **Execution:** If GUILTY \> INNOCENT \-\> Player receives DEAD status. Depending on the revealRolesOnDeath flag, their role is revealed.

## **6\. Win Conditions**

Checked by the WinConditionChecker class after every status change (death at night or lynch).

1. **Mafia Victory:** Number of alive Mafiosi \>= Number of alive Town members.  
2. **Town Victory:** Number of alive Mafiosi \== 0\.

Upon meeting a condition, the game ends, and a summary of roles and statistics is displayed.

## **7\. Error Handling and Validation**

### **7.1. ActionValidator**

Class verifying the logical correctness of the action returned by the LLM:

* Is the target alive?  
* Is the target different from self (unless rules allow)?  
* Is the Sheriff/Doctor using their power (and not trying to "kill")?

### **7.2. Fallback Mechanism**

In case of a technical error (API timeout, malformed JSON) or logical error (invalid action):

1. Retry with an error message in the prompt ("Invalid JSON format, fix it").  
2. After exhausting retry limit: Default action (SKIP for voting, no action for functional roles, random target for Mafia as a last resort).

## **8\. Non-functional Requirements**

* **Logging:** Full record of gameplay (including "thoughts" hidden in the JSON thought field) to a text file for post-mortem analysis.  
* **Costs:** Monitoring of token counts (input/output) and reporting estimated cost after game completion.