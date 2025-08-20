package com.liimand.bettinggameserver.service;

import com.liimand.bettinggameserver.domain.WinnerInfo;

import java.math.BigDecimal;
import java.util.List;

public interface GameListener {
    void onRoundOpened(long roundId, long closesAtMs);
    void onRoundSettled(long roundId, int winningNumber);
    void onWinnersAnnounced(long roundId, List<WinnerInfo> winners);
    void onPlayerResult(long roundId, String nickname, BigDecimal payout);
}
