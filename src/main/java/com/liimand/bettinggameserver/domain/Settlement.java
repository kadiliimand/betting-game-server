package com.liimand.bettinggameserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Settlement {
    private long roundId;
    private int winningNumber;
    private List<WinnerInfo> winners;
}
