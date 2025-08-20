package com.liimand.bettinggameserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bet {
    private String nickname;
    private int number;
    private BigDecimal amount;
}
