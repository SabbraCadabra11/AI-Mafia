# AI Mafia Game Engine

A Java 21 console application where 10 autonomous AI agents play the social deduction game "Mafia" through the OpenRouter API.

## 🎭 Overview

The concept has been shamelessly stolen from Turing Games channel on YouTube (https://www.youtube.com/@turing_games, https://youtu.be/JhBtg-lyKdo). This project implements a fully automated Mafia game where AI agents (Large Language Models) take on roles like Mafia, Sheriff, Doctor, and Villagers. The game runs autonomously with no human intervention, featuring:

- **10 AI players** with distinct roles and goals
- **Night phase** with Mafia consensus, Sheriff investigations, and Doctor protection
- **Day discussion** with sequential speaking and information sharing
- **Voting/Trial phase** with nominations, defense speeches, and judgment
- **Complete game logging** for post-game analysis

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.8+
- OpenRouter API key ([Get one here](https://openrouter.ai/))

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/ai-mafia.git
cd ai-mafia
```

2. Set your OpenRouter API key:
```bash
# Windows
set OPENROUTER_API_KEY=your-api-key-here

# Linux/Mac
export OPENROUTER_API_KEY=your-api-key-here
```

3. Build the project:
```bash
mvn clean package
```

4. Run the game:
```bash
mvn exec:java
# or
java -jar target/ai-mafia-1.0.0-SNAPSHOT.jar
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

```properties
# API Settings
openrouter.api.key=${OPENROUTER_API_KEY}

# Per-Player Models - Each player uses a different LLM!
game.player.1.model=google/gemini-3-flash-preview
game.player.2.model=google/gemini-3-pro-preview
game.player.3.model=openai/gpt-5.2
game.player.4.model=openai/gpt-4o-2024-11-20
game.player.5.model=x-ai/grok-4.1-fast
game.player.6.model=anthropic/claude-sonnet-4.5
game.player.7.model=mistralai/mistral-medium-3.1
game.player.8.model=qwen/qwen3-max
game.player.9.model=deepseek/deepseek-v3.2
game.player.10.model=moonshotai/kimi-k2-thinking
game.player.11.model=minimax/minimax-m2.1
game.player.12.model=z-ai/glm-4.7

# Game Settings
game.player.count=12
game.mafia.count=4
game.max.discussion.rounds=2
game.reveal.roles.on.death=true

# Player Roles
game.mafia.players=3,4,5,6
game.doctor.player=1
game.sheriff.player=2
```

### Multi-Model Gameplay

The game supports **different LLMs competing against each other**! Each player is powered by a different AI model, allowing you to observe:
- Which models are better at deception (Mafia)
- Which models excel at investigation (Sheriff)
- How different models handle social deduction and consensus-building

Any model available on OpenRouter can be used.

## Game Rules

### Roles

| Role | Team | Night Action |
|------|------|--------------|
| **Mafia** | Mafia | Kill one Town member (requires consensus) |
| **Sheriff** | Town | Investigate one player (learn if Mafia or Town) |
| **Doctor** | Town | Protect one player from death |
| **Villager** | Town | No night action |

### Win Conditions

- **Town wins**: All Mafia members are eliminated
- **Mafia wins**: Mafia members equal or outnumber Town

### Game Flow

1. **Night Phase**
   - Mafia discuss and vote on a target (sequential consensus)
   - Sheriff investigates a player
   - Doctor protects a player
   - Night resolves (death or save)

2. **Day Discussion**
   - Players speak in random order
   - Multiple rounds of discussion
   - Information sharing and accusations

3. **Day Voting**
   - Stage A: Parallel nomination
   - Stage B: Accused makes defense speech
   - Stage C: Final judgment (GUILTY/INNOCENT)

## Output

The game produces:
- **Console output**: Real-time game events
- **Game log file**: `logs/mafia-game-{timestamp}.log`. Models use player IDs (e.g. "Player_1" or abbreviations like "P1") thus after the game use find-and-replace tool to replace player IDs with model names.
- **Token usage report**: Cost estimation at game end

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

## Project Structure

```
src/main/java/com/aimafia/
├── Main.java                    # Entry point
├── model/                       # Data model
│   ├── Role.java               # Player roles enum
│   ├── Status.java             # Alive/Dead status
│   ├── Phase.java              # Game phases
│   ├── Player.java             # Player entity
│   └── GameState.java          # Global game state
├── config/
│   └── GameConfig.java         # Configuration singleton
├── ai/
│   ├── LLMResponse.java        # AI response DTO
│   ├── PromptBuilder.java      # Prompt construction
│   └── OpenRouterService.java  # API client
├── validation/
│   └── ActionValidator.java    # Action validation
├── engine/
│   ├── GameEngine.java         # Main game controller
│   ├── NightPhaseHandler.java  # Night logic
│   ├── DayPhaseHandler.java    # Discussion logic
│   ├── VotingHandler.java      # Voting/trial logic
│   ├── WinConditionChecker.java # Win detection
│   └── NightResult.java        # Night outcome DTO
└── util/
    ├── TokenTracker.java       # API usage tracking
    └── GameLogger.java         # Game event logging
```

### Virtual Threads Usage

The game uses Java 21 Virtual Threads for:
- Parallel Sheriff/Doctor actions during night
- Parallel nomination voting
- Parallel final judgment voting

This allows efficient handling of multiple concurrent API calls without blocking OS threads.
