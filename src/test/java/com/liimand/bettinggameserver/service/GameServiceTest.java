package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.domain.RoundState;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Test
    void shouldStartAndCloseRound() {
        RoundInfo open = gameService.startNewRound();

        assertThat(open.state()).isEqualTo(RoundState.OPEN);
        assertThat(open.winningNumber()).isNull();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> {
                    RoundInfo snap = gameService.getCurrentRoundSnapshot();
                    return snap != null && snap.state() == RoundState.CLOSED;
                });

        RoundInfo closed = gameService.getCurrentRoundSnapshot();
        assertThat(closed.state()).isEqualTo(RoundState.CLOSED);
        assertThat(closed.winningNumber()).isBetween(1, 10);
        assertThat(closed.roundId()).isEqualTo(open.roundId());
    }

    @Test
    void shouldStartMultipleRounds() {
        RoundInfo round1 = gameService.startNewRound();
        RoundInfo round2 = gameService.startNewRound();

        assertThat(round2.roundId()).isGreaterThan(round1.roundId());
        assertThat(round2.state()).isEqualTo(RoundState.OPEN);
        assertThat(round2.winningNumber()).isNull();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> gameService.getCurrentRoundSnapshot().state() == RoundState.CLOSED);

        RoundInfo closed = gameService.getCurrentRoundSnapshot();
        assertThat(closed.roundId()).isEqualTo(round2.roundId());
        assertThat(closed.winningNumber()).isBetween(1, 10);
    }

}
