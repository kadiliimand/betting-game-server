package com.liimand.bettinggameserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liimand.bettinggameserver.service.GameService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "game.betting-window-seconds=1",
        "game.auto-repeat=false"
})
class GameControllerIntegTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private GameService gameService;

    @Test
    void shouldStartRound() throws Exception {
        // start round
        mvc.perform(post("/api/rounds/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"));

        // place bet (valid)
        mvc.perform(post("/api/bets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"nickname":"Joe","number":7,"amount":10.00}
                        """))
                .andExpect(status().isAccepted());

        // duplicate bet
        mvc.perform(post("/api/bets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"nickname":"Joe","number":7,"amount":5}
                        """))
                .andExpect(status().isConflict());

        // invalid bet (number)
        mvc.perform(post("/api/bets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"nickname":"Trinity","number":11,"amount":5}
                        """))
                .andExpect(status().isBadRequest());

        // wait until CLOSED
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                mvc.perform(get("/api/rounds/current"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.state").value("CLOSED"))
        );

        mvc.perform(get("/api/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").exists())
                .andExpect(jsonPath("$.winningNumber").isNumber())
                .andReturn();

        assertThat(gameService.getLastSettlement()).isNotNull();
    }
}
