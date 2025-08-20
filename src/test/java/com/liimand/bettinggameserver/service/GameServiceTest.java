package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.*;
import com.liimand.bettinggameserver.util.WinningNumberGenerator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=false",
        "game.repeat-delay-ms=0"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GameServiceTest {

    @Autowired
    private GameService gameService;
    @MockBean
    private WinningNumberGenerator rng;

    private final CapturingListener listener = new CapturingListener();

    @BeforeEach
    void registerListener() {
        gameService.registerListener(listener);
        when(rng.next1to10()).thenReturn(7);
    }

    @Test
    void shouldGetNullCurrentSnapshotBeforeAnyRound() {
        assertThat(gameService.getCurrentRoundSnapshot()).isNull();
        assertThat(gameService.getLastSettlement()).isNull();
    }

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
        assertThat(listener.openedRounds).contains(open.roundId());
        assertThat(listener.settledRounds).contains(open.roundId());
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

    @Test
    void shouldSetBets() {
        gameService.startNewRound();

        assertThat(gameService.placeBet(null)).isEqualTo(PlaceBetResult.INVALID);
        assertThat(gameService.placeBet(new Bet("", 5, new BigDecimal("1"))))
                .isEqualTo(PlaceBetResult.INVALID);
        assertThat(gameService.placeBet(new Bet("John", 0, new BigDecimal("1"))))
                .isEqualTo(PlaceBetResult.INVALID);
        assertThat(gameService.placeBet(new Bet("John", 11, new BigDecimal("1"))))
                .isEqualTo(PlaceBetResult.INVALID);
        assertThat(gameService.placeBet(new Bet("John", 7, new BigDecimal("0"))))
                .isEqualTo(PlaceBetResult.INVALID);

        // accepted (trimming nickname)
        assertThat(gameService.placeBet(new Bet("  John  ", 7, new BigDecimal("10"))))
                .isEqualTo(PlaceBetResult.ACCEPTED);

        // duplicate
        assertThat(gameService.placeBet(new Bet("John", 7, new BigDecimal("5"))))
                .isEqualTo(PlaceBetResult.DUPLICATE);

        // another (losing) bettor
        assertThat(gameService.placeBet(new Bet("Smith", 3, new BigDecimal("2"))))
                .isEqualTo(PlaceBetResult.ACCEPTED);

        // wait for close
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> gameService.getCurrentRoundSnapshot().state() == RoundState.CLOSED);

        // placing after close -> CLOSED
        assertThat(gameService.placeBet(new Bet("Jane", 7, new BigDecimal("1"))))
                .isEqualTo(PlaceBetResult.CLOSED);

        Settlement s = gameService.getLastSettlement();
        assertThat(s).isNotNull();
        assertThat(s.getWinningNumber()).isEqualTo(7);
        assertThat(s.getWinners()).hasSize(1);
        assertThat(s.getWinners().get(0).getNickname()).isEqualTo("John");
        assertThat(s.getWinners().get(0).getWinnings()).isEqualByComparingTo("99.00");

        assertThat(listener.playerResults).anySatisfy(pr -> {
            if (pr.nickname.equals("John")) {
                assertThat(pr.payout).isEqualByComparingTo("99.00");
            }
        });
        assertThat(listener.playerResults).anySatisfy(pr -> {
            if (pr.nickname.equals("Smith")) {
                assertThat(pr.payout).isEqualByComparingTo("0.00");
            }
        });

        assertThat(listener.winnersBroadcasted).isTrue();
    }

    private static class CapturingListener implements GameListener {
        final List<Long> openedRounds = new ArrayList<>();
        final List<Long> settledRounds = new ArrayList<>();
        final List<PlayerResult> playerResults = new ArrayList<>();
        boolean winnersBroadcasted = false;

        @Override
        public void onRoundOpened(long roundId, long closesAtMs) { openedRounds.add(roundId); }

        @Override
        public void onRoundSettled(long roundId, int winningNumber) { settledRounds.add(roundId); }

        @Override
        public void onWinnersAnnounced(long roundId, List<WinnerInfo> winners) {
            winnersBroadcasted = true;
        }

        @Override
        public void onPlayerResult(long roundId, String nickname, BigDecimal payout) {
            playerResults.add(new PlayerResult(roundId, nickname, payout));
        }

        record PlayerResult(long roundId, String nickname, BigDecimal payout) {}
    }
}
