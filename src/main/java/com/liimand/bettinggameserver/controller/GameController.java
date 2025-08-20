package com.liimand.bettinggameserver.controller;

import com.liimand.bettinggameserver.domain.Bet;
import com.liimand.bettinggameserver.domain.PlaceBetResult;
import com.liimand.bettinggameserver.domain.RoundInfo;
import com.liimand.bettinggameserver.domain.Settlement;
import com.liimand.bettinggameserver.domain.mapper.RoundInfoMapper;
import com.liimand.bettinggameserver.domain.mapper.SettlementMapper;
import com.liimand.bettinggameserver.dto.BetRequest;
import com.liimand.bettinggameserver.dto.ErrorDto;
import com.liimand.bettinggameserver.dto.RoundDto;
import com.liimand.bettinggameserver.dto.SettlementDto;
import com.liimand.bettinggameserver.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final RoundInfoMapper roundInfoMapper;
    private final SettlementMapper settlementMapper;

    @PostMapping("/rounds/start")
    public ResponseEntity<RoundDto> startRound() {
        RoundInfo round = gameService.startNewRound();
        return ResponseEntity.ok(roundInfoMapper.toDto(round));
    }

    @GetMapping("/rounds/current")
    public ResponseEntity<RoundDto> currentRound() {
        RoundInfo round = gameService.getCurrentRoundSnapshot();
        if (round == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(roundInfoMapper.toDto(round));
    }

    @PostMapping("/bets")
    public ResponseEntity<?> placeBet(@Valid @RequestBody BetRequest req) {
        PlaceBetResult res = gameService.placeBet(new Bet(req.nickname(), req.number(), req.amount()));
        return switch (res) {
            case ACCEPTED -> ResponseEntity.accepted().build();
            case CLOSED -> ResponseEntity.status(409).body(new ErrorDto("ROUND_CLOSED", "Betting is closed"));
            case DUPLICATE -> ResponseEntity.status(409).body(new ErrorDto("DUPLICATE", "You have already placed a bet this round"));
            case INVALID -> ResponseEntity.badRequest().body(new ErrorDto("INVALID", "Invalid bet"));
        };
    }

    @GetMapping("/settlement")
    public ResponseEntity<SettlementDto> lastSettlement() {
        Settlement settlement = gameService.getLastSettlement();
        if (settlement == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(settlementMapper.toDto(settlement));
    }
}
