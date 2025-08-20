package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.*;
import com.liimand.bettinggameserver.util.WinningNumberGenerator;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class GameService {
    private static final BigDecimal PAYOUT_MULTIPLIER = new BigDecimal("9.9");

    private final ScheduledExecutorService scheduler;
    private final WinningNumberGenerator rng;

    @Value("${game.betting-window-seconds}")
    private long bettingWindowSeconds;

    @Value("${game.auto-repeat}")
    private boolean autoRepeat;

    @Value("${game.repeat-delay-ms}")
    private long repeatDelayMs;

    private Duration bettingWindow;

    private final AtomicLong roundSeq = new AtomicLong(0);
    private final AtomicReference<RoundInfo> roundRef = new AtomicReference<>();

    private volatile RoundBets currentBets;
    @Getter
    private volatile Settlement lastSettlement;

    private final List<GameListener> listeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    void init() {
        this.bettingWindow = Duration.ofSeconds(bettingWindowSeconds);
    }

    public void registerListener(GameListener l) {
        if (l != null) listeners.add(l);
    }

    public synchronized RoundInfo startNewRound() {
        long id = roundSeq.incrementAndGet();
        Instant now = Instant.now();
        Instant closesAt = now.plus(bettingWindow);

        RoundInfo open = new RoundInfo(id, RoundState.OPEN, now, closesAt, null);
        roundRef.set(open);
        currentBets = new RoundBets(id);
        lastSettlement = null;

        listeners.forEach(l -> l.onRoundOpened(id, closesAt.toEpochMilli()));

        scheduler.schedule(this::closeAndSettle, bettingWindow.toMillis(), TimeUnit.MILLISECONDS);
        return open;
    }

    public RoundInfo getCurrentRoundSnapshot() {
        return roundRef.get();
    }

    public PlaceBetResult placeBet(Bet bet) {
        if (bet == null || bet.getNickname() == null || bet.getNickname().isBlank()) return PlaceBetResult.INVALID;
        if (bet.getNumber() < 1 || bet.getNumber() > 10) return PlaceBetResult.INVALID;
        if (bet.getAmount() == null || bet.getAmount().compareTo(BigDecimal.ZERO) <= 0) return PlaceBetResult.INVALID;

        RoundInfo r = roundRef.get();
        if (r == null || r.state() != RoundState.OPEN || Instant.now().isAfter(r.bettingClosesAt())) {
            return PlaceBetResult.CLOSED;
        }
        if (currentBets == null || currentBets.roundId != r.roundId()) {
            return PlaceBetResult.CLOSED;
        }

        Bet copy = new Bet(bet.getNickname().trim(), bet.getNumber(), bet.getAmount());
        Bet prev = currentBets.bets.putIfAbsent(copy.getNickname(), copy);
        return (prev == null) ? PlaceBetResult.ACCEPTED : PlaceBetResult.DUPLICATE;
    }

    private synchronized void closeAndSettle() {
        RoundInfo current = roundRef.get();
        if (current == null || current.state() == RoundState.CLOSED) return;

        int winning = rng.next1to10();
        RoundInfo closed = new RoundInfo(
                current.roundId(), RoundState.CLOSED,
                current.openedAt(), current.bettingClosesAt(),
                winning
        );
        roundRef.set(closed);

        List<WinnerInfo> winners = new ArrayList<>();
        if (currentBets != null && currentBets.roundId == current.roundId()) {
            for (Bet b : currentBets.bets.values()) {
                BigDecimal payout = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                if (b.getNumber() == winning) {
                    payout = b.getAmount().multiply(PAYOUT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
                    winners.add(new WinnerInfo(b.getNickname(), payout));
                }
                for (GameListener l : listeners) {
                    l.onPlayerResult(current.roundId(), b.getNickname(), payout);
                }
            }
        }

        lastSettlement = new Settlement(current.roundId(), winning, winners);

        for (GameListener l : listeners) {
            l.onWinnersAnnounced(current.roundId(), winners);
            l.onRoundSettled(current.roundId(), winning);
        }

        if (autoRepeat) {
            scheduler.schedule(this::startNewRound, repeatDelayMs, TimeUnit.MILLISECONDS);
        }
    }


    private static final class RoundBets {
        final long roundId;
        final ConcurrentMap<String, Bet> bets = new ConcurrentHashMap<>();
        RoundBets(long roundId) { this.roundId = roundId; }
    }
}
