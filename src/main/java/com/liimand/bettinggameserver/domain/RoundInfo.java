package com.liimand.bettinggameserver.domain;

import java.time.Instant;

public record RoundInfo(
        long roundId,
        RoundState state,
        Instant openedAt,
        Instant bettingClosesAt,
        Integer winningNumber
) {}
