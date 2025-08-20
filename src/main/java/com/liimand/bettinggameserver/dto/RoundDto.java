package com.liimand.bettinggameserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoundDto {
    private long roundId;
    private String state;
    private long openedAtMs;
    private long bettingClosesAtMs;
    private Integer winningNumber;
}
