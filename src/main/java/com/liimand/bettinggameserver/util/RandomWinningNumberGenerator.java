package com.liimand.bettinggameserver.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomWinningNumberGenerator implements WinningNumberGenerator {
    @Override
    public int next1to10() {
        return ThreadLocalRandom.current().nextInt(1, 11);
    }
}
