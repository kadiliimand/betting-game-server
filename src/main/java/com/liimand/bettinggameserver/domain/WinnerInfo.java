package com.liimand.bettinggameserver.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class WinnerInfo {
    private String nickname;
    private BigDecimal winnings;
}
