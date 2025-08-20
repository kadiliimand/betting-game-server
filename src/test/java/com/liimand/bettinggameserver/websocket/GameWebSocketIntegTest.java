package com.liimand.bettinggameserver.websocket;

import com.liimand.bettinggameserver.service.GameService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=false"
})
class GameWebSocketIntegTest {

    @Value("${local.server.port}") int port;

    @Autowired TestRestTemplate rest;

    @Autowired
    private GameService gameService;

    @Test
    void shouldConnectWebSocket_andReceiveWinners() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client
                .doHandshake(
                        new AbstractWebSocketHandler() {
                            @Override
                            public void handleTextMessage(WebSocketSession s, TextMessage msg) {
                                messages.offer(msg.getPayload());
                            }
                        },
                        new WebSocketHttpHeaders(),
                        URI.create("ws://localhost:" + port + "/ws/game")
                )
                .get();

        rest.postForEntity("/api/rounds/start", null, String.class);
        session.sendMessage(new TextMessage("""
            {"type":"BET","nickname":"John","number":7,"amount":10.00}
        """));

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> gameService.getLastSettlement() != null);

        List<String> seen = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < end) {
            String m = messages.poll(500, TimeUnit.MILLISECONDS); // block a bit
            if (m != null) seen.add(m);
        }

        assertThat(seen.stream().anyMatch(s -> s.contains("\"WINNERS\"")))
                .as("WINNERS message present")
                .isTrue();

        session.close();
    }
}

