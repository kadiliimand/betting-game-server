package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.domain.RoundState;
import com.liimand.bettinggameserver.util.WinningNumberGenerator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=true",
        "game.repeat-delay-ms=50"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GameServiceAutoRepeatTest {

    @Autowired
    private GameService gameService;
    @MockBean
    private WinningNumberGenerator rng;

    @Test
    void shouldAutoRepeatRounds() {
        when(rng.next1to10()).thenReturn(4, 8);

        RoundInfo first = gameService.startNewRound();
        long firstId = first.roundId();

        // wait until the first round closes
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> gameService.getCurrentRoundSnapshot().state() == RoundState.CLOSED);

        // soon after, auto-repeat should start a new OPEN round with higher id
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    RoundInfo snap = gameService.getCurrentRoundSnapshot();
                    assertThat(snap).isNotNull();
                    assertThat(snap.roundId()).isGreaterThan(firstId);
                    assertThat(snap.state()).isEqualTo(RoundState.OPEN);
                });
    }
}
