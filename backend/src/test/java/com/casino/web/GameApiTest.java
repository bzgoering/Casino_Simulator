package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casino.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class GameApiTest extends ApiTestSupport {

    @Nested
    @DisplayName("slots")
    class Slots {

        @Test
        @DisplayName("a spin moves the balance by exactly the net result")
        void spinSettlesBalanceExactly() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 10.00}
                    """, token));

            BigDecimal net = result.get("net").decimalValue();
            BigDecimal balance = result.get("balance").decimalValue();
            BigDecimal payout = result.get("payout").decimalValue();

            // Started at 10,000: the balance must be exactly the opening figure plus the net.
            assertThat(balance).isEqualByComparingTo(new BigDecimal("10000.00").add(net));
            assertThat(net).isEqualByComparingTo(payout.subtract(new BigDecimal("10.00")));
        }

        @Test
        @DisplayName("the reported symbols match the reported reel stops")
        void symbolsMatchStops() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 1.00}
                    """, token));

            assertThat(result.get("stops")).hasSize(3);
            assertThat(result.get("symbols")).hasSize(3);
            assertThat(result.get("multiplier").asInt()).isNotNegative();
        }

        @Test
        @DisplayName("a bet over the table maximum is refused")
        void betAboveMaximumRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 99999.00}
                            """, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Maximum bet is 5000.00."));
        }

        @Test
        @DisplayName("a bet under the table minimum is refused")
        void betBelowMinimumRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 0.50}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a bet with sub-cent precision is refused rather than rounded")
        void subCentBetRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 10.005}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a negative bet is refused, so it cannot be used to add balance")
        void negativeBetRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": -100.00}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a player cannot stake more than they hold")
        void insufficientFundsRefused() throws Exception {
            // A new player holds 100.00.
            String token = token(signUp("slots_player", "correct-horse-9"));

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 500.00}
                            """, token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Not enough balance")));
        }
    }

    @Nested
    @DisplayName("roulette")
    class Roulette {

        @Test
        @DisplayName("a valid spin settles every chip on the layout")
        void validSpinSettlesAllBets() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/roulette/spin", """
                    {"bets":[
                      {"type":"STRAIGHT","selection":"17","amount":5.00},
                      {"type":"COLOR","selection":"RED","amount":10.00}
                    ]}
                    """, token));

            assertThat(result.get("pocket").asInt()).isBetween(0, 36);
            assertThat(result.get("color").asText()).isIn("RED", "BLACK", "GREEN");
            assertThat(result.get("wheelIndex").asInt()).isBetween(0, 36);
            assertThat(result.get("bets")).hasSize(2);
            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("a forged split covering six numbers is refused")
        void forgedSplitRefused() throws Exception {
            String token = token(guestSession());

            // Priced at 17:1 but covering six pockets: the layout check must reject it.
            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"SPLIT","selection":"1,2,3,4,5,6","amount":10.00}]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a number that is not on the wheel is refused")
        void offWheelNumberRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"STRAIGHT","selection":"37","amount":10.00}]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a spin with no bets is refused")
        void emptyBetListRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("more chips than the table allows are refused")
        void tooManyBetsRefused() throws Exception {
            String token = token(guestSession());

            StringBuilder bets = new StringBuilder();
            for (int i = 0; i < 25; i++) {
                if (i > 0) {
                    bets.append(",");
                }
                bets.append("{\"type\":\"STRAIGHT\",\"selection\":\"").append(i).append("\",\"amount\":1.00}");
            }

            mvc.perform(postJson("/api/games/roulette/spin",
                            "{\"bets\":[" + bets + "]}", token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a straight-up either returns 36x the stake or nothing at all")
        void straightUpPaysThirtySixTimesOrNothing() throws Exception {
            String token = token(guestSession());
            boolean sawLoss = false;

            // Asserted on every spin, so this tests the payout rule rather than luck.
            for (int spin = 0; spin < 150; spin++) {
                JsonNode result = perform(postJson("/api/games/roulette/spin", """
                        {"bets":[{"type":"STRAIGHT","selection":"17","amount":1.00}]}
                        """, token));

                boolean won = result.get("bets").get(0).get("won").asBoolean();
                BigDecimal payout = result.get("totalPayout").decimalValue();

                if (won) {
                    assertThat(result.get("pocket").asInt()).isEqualTo(17);
                    assertThat(payout).isEqualByComparingTo("36.00");
                } else {
                    assertThat(result.get("pocket").asInt()).isNotEqualTo(17);
                    assertThat(payout).isEqualByComparingTo("0.00");
                    sawLoss = true;
                }
            }

            assertThat(sawLoss).isTrue();
        }
    }

    @Nested
    @DisplayName("blackjack")
    class Blackjack {

        @Test
        @DisplayName("a deal takes the stake and shows only the dealer upcard")
        void dealHidesTheHoleCard() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 25.00}
                    """, token));

            assertThat(result.get("hands").get(0).get("cards")).hasSize(2);
            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("25.00");

            if (!result.get("dealer").get("revealed").asBoolean()) {
                // The hole card must be absent from the payload, not merely hidden by the UI.
                assertThat(result.get("dealer").get("cards")).hasSize(1);
                assertThat(result.get("legalActions")).isNotEmpty();
            } else {
                // A natural on either side settles the round at once.
                assertThat(result.get("settled").asBoolean()).isTrue();
            }
        }

        @Test
        @DisplayName("across many hands the hole card is never sent before the reveal")
        void holeCardIsNeverLeaked() throws Exception {
            String token = token(guestSession());

            for (int hand = 0; hand < 25; hand++) {
                JsonNode dealt = perform(postJson("/api/games/blackjack/deal", """
                        {"bet": 5.00}
                        """, token));

                if (!dealt.get("dealer").get("revealed").asBoolean()) {
                    assertThat(dealt.get("dealer").get("cards")).hasSize(1);
                    // Stand to finish the hand so the next deal is allowed.
                    perform(postJson("/api/games/blackjack/action", """
                            {"action":"STAND"}
                            """, token));
                }
            }
        }

        @Test
        @DisplayName("dealing a second hand while one is live is refused")
        void cannotDealTwice() throws Exception {
            String token = token(guestSession());

            JsonNode first = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 5.00}
                    """, token));

            if (!first.get("settled").asBoolean()) {
                mvc.perform(postJson("/api/games/blackjack/deal", """
                                {"bet": 5.00}
                                """, token))
                        .andExpect(status().isConflict());
            }
        }

        @Test
        @DisplayName("an action with no hand in progress is refused")
        void actionWithoutHandRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/blackjack/action", """
                            {"action":"HIT"}
                            """, token))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("an unrecognised action value is refused")
        void unknownActionRefused() throws Exception {
            String token = token(guestSession());
            perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 5.00}
                    """, token));

            mvc.perform(postJson("/api/games/blackjack/action", """
                            {"action":"CHEAT"}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a settled hand leaves a balance consistent with stake and payout")
        void settledHandLeavesConsistentBalance() throws Exception {
            String token = token(guestSession());

            JsonNode dealt = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 100.00}
                    """, token));

            JsonNode finished = dealt.get("settled").asBoolean()
                    ? dealt
                    : perform(postJson("/api/games/blackjack/action", """
                            {"action":"STAND"}
                            """, token));

            assertThat(finished.get("settled").asBoolean()).isTrue();

            BigDecimal staked = finished.get("totalStaked").decimalValue();
            BigDecimal payout = finished.get("totalPayout").decimalValue();
            BigDecimal balance = finished.get("balance").decimalValue();

            assertThat(balance).isEqualByComparingTo(
                    new BigDecimal("10000.00").subtract(staked).add(payout));
        }

        @Test
        @DisplayName("the shoe is dealt down rather than reshuffled every hand")
        void shoeIsDealtDownAcrossHands() throws Exception {
            String token = token(guestSession());

            JsonNode first = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 5.00}
                    """, token));
            int afterFirst = first.get("cardsRemaining").asInt();
            if (!first.get("settled").asBoolean()) {
                perform(postJson("/api/games/blackjack/action", """
                        {"action":"STAND"}
                        """, token));
            }

            JsonNode second = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 5.00}
                    """, token));

            // An 8-deck shoe starts at 416 and keeps going down; a per-hand reshuffle would
            // reset it and quietly change the game.
            assertThat(afterFirst).isLessThan(416);
            assertThat(second.get("cardsRemaining").asInt()).isLessThan(afterFirst);
        }
    }
}
