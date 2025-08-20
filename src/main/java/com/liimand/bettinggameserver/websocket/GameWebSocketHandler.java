package com.liimand.bettinggameserver.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liimand.bettinggameserver.domain.Bet;
import com.liimand.bettinggameserver.domain.PlaceBetResult;
import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.domain.WinnerInfo;
import com.liimand.bettinggameserver.service.GameListener;
import com.liimand.bettinggameserver.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler, GameListener {

    private final GameService gameService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Set<WebSocketSession>> sessionsByNick = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> nickBySessionId = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    void init() { gameService.registerListener(this); }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        RoundInfo r = gameService.getCurrentRoundSnapshot();
        if (r != null) {
            send(session, new OutRoundOpened(r.roundId(), r.bettingClosesAt().toEpochMilli()));
            if (r.winningNumber() != null) {
                send(session, new OutRoundSettled(r.roundId(), r.winningNumber()));
            }
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (!(message instanceof TextMessage tm)) return;
        try {
            InMessage in = mapper.readValue(tm.getPayload(), InMessage.class);
            if (!"BET".equalsIgnoreCase(in.type)) return;

            if (in.nickname == null || in.nickname.isBlank()) {
                send(session, new OutError("VALIDATION", "nickname required")); return;
            }
            if (in.number < 1 || in.number > 10) {
                send(session, new OutError("VALIDATION", "number must be 1..10")); return;
            }
            if (in.amount == null || in.amount.compareTo(BigDecimal.ZERO) <= 0) {
                send(session, new OutError("VALIDATION", "amount must be > 0")); return;
            }

            PlaceBetResult res = gameService.placeBet(new Bet(in.nickname.trim(), in.number, in.amount));
            switch (res) {
                case ACCEPTED -> {
                    bindSessionToNickname(session, in.nickname.trim());
                    send(session, new OutAck("BET_ACCEPTED"));
                }
                case DUPLICATE -> send(session, new OutError("DUPLICATE", "bet already placed this round"));
                case CLOSED -> send(session, new OutError("ROUND_CLOSED", "betting is closed"));
                case INVALID -> send(session, new OutError("INVALID", "invalid bet"));
            }

        } catch (Exception e) {
            send(session, new OutError("BAD_JSON", e.getMessage()));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        try { send(session, new OutError("TRANSPORT", exception.getMessage())); } catch (Exception ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session);
        String nick = nickBySessionId.remove(session.getId());
        if (nick != null) {
            Set<WebSocketSession> set = sessionsByNick.get(nick);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) sessionsByNick.remove(nick);
            }
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public void onRoundOpened(long roundId, long closesAtMs) {
        broadcast(new OutRoundOpened(roundId, closesAtMs));
    }

    @Override
    public void onRoundSettled(long roundId, int winningNumber) {
        broadcast(new OutRoundSettled(roundId, winningNumber));
    }

    @Override
    public void onWinnersAnnounced(long roundId, List<WinnerInfo> winners) {
        var payload = winners.stream()
                .map(w -> new OutWinners.Winner(w.getNickname(), w.getWinnings()))
                .toList();
        broadcast(new OutWinners(roundId, payload));
    }

    @Override
    public void onPlayerResult(long roundId, String nickname, BigDecimal payout) {
        Set<WebSocketSession> set = sessionsByNick.get(nickname);
        if (set == null || set.isEmpty()) return;
        OutYourResult msg = new OutYourResult(roundId, payout.signum() > 0 ? "WIN" : "LOSE", payout);
        for (WebSocketSession s : set) {
            if (s.isOpen()) try { send(s, msg); } catch (IOException ignored) {}
        }
    }

    private void bindSessionToNickname(WebSocketSession session, String nickname) {
        nickBySessionId.put(session.getId(), nickname);
        sessionsByNick.computeIfAbsent(nickname, n -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void broadcast(Object payload) {
        String json;
        try { json = mapper.writeValueAsString(payload); } catch (Exception e) { return; }
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try { s.sendMessage(new TextMessage(json)); } catch (IOException ignored) {}
            }
        }
    }

    private void send(WebSocketSession s, Object payload) throws IOException {
        s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
    }

    static final class InMessage {
        public String type;
        public String nickname;
        public int number;
        public BigDecimal amount;
    }

    record OutRoundOpened(String type, long roundId, long closesAtMs) {
        OutRoundOpened(long roundId, long closesAtMs) { this("ROUND_OPENED", roundId, closesAtMs); }
    }
    record OutRoundSettled(String type, long roundId, int winningNumber) {
        OutRoundSettled(long roundId, int winningNumber) { this("ROUND_SETTLED", roundId, winningNumber); }
    }
    record OutAck(String type) {}
    record OutError(String type, String message) {}
    record OutWinners(String type, long roundId, List<Winner> winners) {
        OutWinners(long roundId, List<Winner> winners) { this("WINNERS", roundId, winners); }
        record Winner(String nickname, java.math.BigDecimal winnings) {}
    }
    record OutYourResult(String type, long roundId, String result, java.math.BigDecimal payout) {
        OutYourResult(long roundId, String result, java.math.BigDecimal payout) { this("YOUR_RESULT", roundId, result, payout); }
    }
}
