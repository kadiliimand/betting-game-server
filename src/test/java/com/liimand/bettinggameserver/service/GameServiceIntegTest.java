package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.*;
import com.liimand.bettinggameserver.util.WinningNumberGenerator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=false"
})
class GameServiceIntegTest {
    @TestConfiguration
    static class FixedRngConfig {
        @Bean
        @Primary
        WinningNumberGenerator fixedRng() {
            return () -> 7;
        }
    }

    @Autowired
    private GameService gameService;

    @Test
    void shouldStartNewRoundWithWinner() {
        // Start round
        RoundInfo open = gameService.startNewRound();
        assertThat(open.state()).isEqualTo(RoundState.OPEN);

        // Place valid winning bet on 7
        PlaceBetResult result = gameService.placeBet(new Bet("John", 7, new BigDecimal("10")));
        assertThat(result).isEqualTo(PlaceBetResult.ACCEPTED);

        // Duplicate should be rejected
        PlaceBetResult dup = gameService.placeBet(new Bet("John", 7, new BigDecimal("5")));
        assertThat(dup).isEqualTo(PlaceBetResult.DUPLICATE);

        // Invalid number
        PlaceBetResult badNum = gameService.placeBet(new Bet("Trinity", 11, new BigDecimal("5")));
        assertThat(badNum).isEqualTo(PlaceBetResult.INVALID);

        // Wait until auto-close and settlement
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> gameService.getCurrentRoundSnapshot().state() == RoundState.CLOSED);

        RoundInfo closed = gameService.getCurrentRoundSnapshot();
        assertThat(closed.winningNumber()).isEqualTo(7);

        Settlement settlement = gameService.getLastSettlement();
        assertThat(settlement).isNotNull();
        assertThat(settlement.getRoundId()).isEqualTo(open.roundId());
        assertThat(settlement.getWinningNumber()).isEqualTo(7);

        // Winners list should include John with 10 * 9.9 = 99.00
        assertThat(settlement.getWinners()).anySatisfy(w -> {
            assertThat(w.getNickname()).isEqualTo("John");
            assertThat(w.getWinnings()).isEqualByComparingTo(new BigDecimal("99.00"));
        });
    }

    @Test
    void shouldGetRejectedBetBecauseRoundIsClosed() {
        gameService.startNewRound();
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> gameService.getCurrentRoundSnapshot().state() == RoundState.CLOSED);

        PlaceBetResult result = gameService.placeBet(new Bet("Joe", 3, new BigDecimal("1")));
        assertThat(result).isEqualTo(PlaceBetResult.CLOSED);
    }
}
