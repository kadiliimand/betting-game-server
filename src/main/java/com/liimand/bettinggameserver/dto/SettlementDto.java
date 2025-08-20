package com.liimand.bettinggameserver.dto;

import java.math.BigDecimal;
import java.util.List;

public record SettlementDto(
        long roundId,
        int winningNumber,
        List<WinnerDto> winners
) {
    public record WinnerDto(String nickname, BigDecimal winnings) {}
}
