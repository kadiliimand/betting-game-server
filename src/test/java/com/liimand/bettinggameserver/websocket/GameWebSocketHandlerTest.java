package com.liimand.bettinggameserver.websocket;

import com.liimand.bettinggameserver.domain.PlaceBetResult;
import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.domain.RoundState;
import com.liimand.bettinggameserver.domain.WinnerInfo;
import com.liimand.bettinggameserver.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameWebSocketHandlerTest {

    @Mock
    private GameService gameService;

    @Mock
    private WebSocketSession session;

    private GameWebSocketHandler handler;

    @BeforeEach
    void setup() {
        handler = new GameWebSocketHandler(gameService);
    }

    @Test
    void shouldSendRoundOpenedAndSettled() throws Exception {
        RoundInfo round = new RoundInfo(1, RoundState.CLOSED, Instant.now(), Instant.now().plusSeconds(10), 7);
        when(gameService.getCurrentRoundSnapshot()).thenReturn(round);

        handler.afterConnectionEstablished(session);

        verify(session, atLeastOnce()).sendMessage(argThat(msg -> {
            if (msg instanceof TextMessage tm) {
                String payload = tm.getPayload();
                return payload.contains("ROUND_OPENED") || payload.contains("ROUND_SETTLED");
            }
            return false;
        }));
    }

    @Test
    void shouldAcceptValidBet() throws Exception {
        String payload = """
            {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """;
        when(gameService.placeBet(any())).thenReturn(PlaceBetResult.ACCEPTED);
        when(session.getId()).thenReturn("s1");

        handler.handleMessage(session, new TextMessage(payload));

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("BET_ACCEPTED"))
        );
    }
    @Test
    void shouldRejectDuplicateBet() throws Exception {
        String payload = """
            {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """;
        when(gameService.placeBet(any())).thenReturn(PlaceBetResult.DUPLICATE);

        handler.handleMessage(session, new TextMessage(payload));

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("DUPLICATE"))
        );
    }

    @Test
    void shouldRejectClosedBet() throws Exception {
        String payload = """
            {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """;
        when(gameService.placeBet(any())).thenReturn(PlaceBetResult.CLOSED);

        handler.handleMessage(session, new TextMessage(payload));

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("ROUND_CLOSED"))
        );
    }

    @Test
    void shouldRejectInvalidBet() throws Exception {
        String payload = """
            {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """;
        when(gameService.placeBet(any())).thenReturn(PlaceBetResult.INVALID);

        handler.handleMessage(session, new TextMessage(payload));

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("INVALID"))
        );
    }

    @Test
    void shouldSendValidationErrors() throws Exception {
        // missing nickname
        handler.handleMessage(session, new TextMessage("""
            {"type":"BET","number":5,"amount":10}
        """));
        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("nickname required"))
        );

        // invalid number
        handler.handleMessage(session, new TextMessage("""
            {"type":"BET","nickname":"Joe","number":11,"amount":10}
        """));
        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("number must be 1..10"))
        );

        // invalid amount
        handler.handleMessage(session, new TextMessage("""
            {"type":"BET","nickname":"Joe","number":5,"amount":0}
        """));
        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("amount must be > 0"))
        );
    }

    @Test
    void shouldSendBadJsonError() throws Exception {
        handler.handleMessage(session, new TextMessage("{ not a json }"));
        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("BAD_JSON"))
        );
    }

    @Test
    void shouldSendWinAndLoss() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        when(gameService.placeBet(any())).thenReturn(PlaceBetResult.ACCEPTED);

        handler.handleMessage(session, new TextMessage("""
        {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """));
        reset(session);
        when(session.isOpen()).thenReturn(true);

        handler.onPlayerResult(1, "Joe", new BigDecimal("99.00"));
        handler.onPlayerResult(1, "Joe", BigDecimal.ZERO);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeast(2)).sendMessage(captor.capture());

        List<String> payloads = captor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .toList();

        long yourResultCount = payloads.stream().filter(p -> p.contains("\"YOUR_RESULT\"")).count();
        assertThat(yourResultCount).isEqualTo(2);
        assertThat(String.join("\n", payloads)).contains("\"result\":\"WIN\"");
        assertThat(String.join("\n", payloads)).contains("\"result\":\"LOSE\"");
    }

    @Test
    void shouldBroadcastRoundOpened() throws Exception {
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.onRoundOpened(1L, 1000L);

        verify(session, atLeastOnce()).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm &&
                        tm.getPayload().contains("ROUND_OPENED"))
        );
    }

    @Test
    void shouldBroadcastRoundSettled() throws Exception {
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.onRoundSettled(1, 7);

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("ROUND_SETTLED"))
        );
    }

    @Test
    void shouldBroadcastWinners() throws Exception {
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        List<WinnerInfo> winners = List.of(new WinnerInfo("Joe", BigDecimal.TEN));
        handler.onWinnersAnnounced(1, winners);

        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("WINNERS"))
        );
    }

    @Test
    void shouldSendTransportError() throws Exception {
        handler.handleTransportError(session, new RuntimeException("boom"));
        verify(session).sendMessage(
                argThat(msg -> msg instanceof TextMessage tm && tm.getPayload().contains("TRANSPORT"))
        );
    }

    @Test
    void shouldCleanupAfterConnectionClosed() {
        when(session.getId()).thenReturn("s1");

        assertDoesNotThrow(() -> {
            handler.handleMessage(session, new TextMessage("""
            {"type":"BET","nickname":"Joe","number":5,"amount":10}
        """));

            handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        });
    }


    @Test
    void supportsPartialMessages_shouldReturnFalse() {
        assertThat(handler.supportsPartialMessages()).isFalse();
    }
}
