# Betting Game Server 🎲
A simple multiplayer betting game implemented with Spring Boot and WebSockets.  
Players connect via REST and WebSocket APIs to place bets and receive real-time game updates.

## 🎮 Game Rules
- The server starts a round with a configurable betting window (default: 10 seconds).
- Players place bets on numbers **1–10** with an amount.
- When the round closes, the server picks a random winning number.
- **Payouts are 9.9× the stake** for correct guesses.
- All players receive a `WINNERS` broadcast message with the list of winners.
- Each player also gets a private `YOUR_RESULT` message:
    - `"WIN"` with payout amount
    - `"LOSE"` with payout = `0`
- The process repeats automatically if `game.auto-repeat=true`.

## ⚙️ Technology
- **Java 17+**
- **Spring Boot**
- **WebSocket API**
- **Gradle**
- **Awaitility** for async test waits
- **JUnit 5 & Mockito** for unit/integration tests

## 📡 API Overview
### REST Endpoints
- `POST /api/rounds/start` – Start new round
- `GET /api/rounds/current` – Current round info
- `POST /api/bets` – Place bet
  ```json
  { "nickname": "Joe", "number": 7, "amount": 10.00 }
  ```
- `GET /api/rounds/current` – Current round info

### WebSocket Endpoint
- `ws://localhost:8080/ws/game`

### Incoming messages
```json
{ "type": "BET", "nickname": "Joe", "number": 7, "amount": 10.00 }
```

### Outgoing messages
```json
{ "type": "ROUND_OPENED", "roundId": 1, "closesAtMs": 1690001234567 }
{ "type": "ROUND_SETTLED", "roundId": 1, "winningNumber": 7 } 
{ "type": "WINNERS", "roundId": 1, "winners": [{ "nickname": "Joe", "winnings": 99.00 }] }
{ "type": "YOUR_RESULT", "roundId": 1, "result": "WIN", "payout": 99.00 }
{ "type": "BET_ACCEPTED" }
{ "type": "ROUND_CLOSED" | "DUPLICATE" | "INVALID" | "VALIDATION" | "BAD_JSON", "message": "..." }
```

## 🧪 Testing
The project comes with comprehensive test coverage (~100%):
- `Service layer`
- `GameServiceTest` – Round lifecycle, betting validation
- `GameServiceIntegTest` – Integration with scheduler
- `GameServiceAutoRepeatTest` – Auto-repeat rounds
- `Controller layer`
- `GameControllerIntegTest` – REST endpoint integration
- `WebSocket layer`
- `GameWebSocketHandlerTest` – Unit tests for handler branches (valid bet, invalid bet, duplicate, JSON errors, events, cleanup)
- `GameWebSocketIntegTest` – End-to-end WebSocket lifecycle & message flow
- `Bootstrap`
- `BettingGameServerApplicationTests` – Application context loads

## 📖 Project Structure
### Main
```
src/main/java/com/liimand/bettinggameserver
├── config          # GameConfig, WebSocketConfig, ApiExceptionHandler
├── controller      # REST API endpoints
├── domain          # Bet, RoundInfo, Settlement, WinnerInfo, RoundState, PlaceBetResult
│    └── mapper     # Mappers: RoundInfoMapper, SettlementMapper
├── dto             # DTOs: BetRequest, ErrorDto, RoundDto, SettlementDto
├── service         # GameService, GameListener
├── util            # utilities
└── websocket       # GameWebSocketHandler
```

### Test
```
src/test/java/com/liimand/bettinggameserver
 ├── controller      # REST controller integration tests
 ├── service         # Unit & integration tests for GameService
 └── websocket       # WebSocket unit & integration tests
```

## 🚀 Highlights
- **Simple architecture** – service-driven game loop with listener-based event propagation
- **Reactive communication** – REST for commands, WebSocket for real-time events
- **Full validation** – invalid bets rejected both via REST and WebSocket
- **Tests-first approach** – async scenarios handled with Awaitility
- **Near-100% coverage** – confidence in game logic and message flows
