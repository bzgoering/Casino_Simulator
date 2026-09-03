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
        @DisplayName("the whole three-by-three window comes back, not just the paid lines")
        void windowMatchesStops() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 1.00, "credits": 1}
                    """, token));

            assertThat(result.get("stops")).hasSize(3);
            assertThat(result.get("window")).hasSize(3);
            result.get("window").forEach(reel -> assertThat(reel).hasSize(3));
            // One credit buys the centre line, so only that line is scored.
            assertThat(result.get("lines")).hasSize(1);
            assertThat(result.get("lines").get(0).get("payline").asText()).isEqualTo("MIDDLE");
            assertThat(result.get("totalMultiplier").asInt()).isNotNegative();
        }

        @Test
        @DisplayName("credits light more lines and cost the bet for each")
        void creditsBuyLines() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 2.00, "credits": 5}
                    """, token));

            assertThat(result.get("credits").asInt()).isEqualTo(5);
            assertThat(result.get("betPerLine").decimalValue()).isEqualByComparingTo("2.00");
            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("10.00");
            assertThat(result.get("lines")).hasSize(5);

            // The payout is the lines added up, each on the per-line bet.
            BigDecimal lineTotal = BigDecimal.ZERO;
            for (JsonNode line : result.get("lines")) {
                lineTotal = lineTotal.add(line.get("payout").decimalValue());
            }
            assertThat(result.get("payout").decimalValue()).isEqualByComparingTo(lineTotal);
            assertThat(result.get("net").decimalValue())
                    .isEqualByComparingTo(lineTotal.subtract(new BigDecimal("10.00")));
        }

        @Test
        @DisplayName("the machine has no minimum: a one-cent bet plays")
        void thereIsNoMinimumBet() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 0.01, "credits": 3}
                    """, token));

            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("0.03");
            assertThat(result.get("lines")).hasSize(3);
        }

        @Test
        @DisplayName("a credit count that is not a button on the cabinet is refused")
        void unlistedCreditCountRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 1.00, "credits": 2}
                            """, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Credits must be 1, 3 or 5."));
        }

        @Test
        @DisplayName("omitting the credits plays a single line")
        void creditsDefaultToOne() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/slots/spin", """
                    {"bet": 1.00}
                    """, token));

            assertThat(result.get("credits").asInt()).isEqualTo(1);
            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("1.00");
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
                    .andExpect(jsonPath("$.message").value("Not enough money."));
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

            // A pocket travels as it is written on the cloth, so "00" survives the trip.
            assertThat(result.get("pocket").asText()).matches("00|[0-9]{1,2}");
            assertThat(result.get("color").asText()).isIn("RED", "BLACK", "GREEN");
            assertThat(result.get("wheelIndex").asInt()).isBetween(0, 37);
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
                            {"bets":[{"type":"STRAIGHT","selection":"38","amount":10.00}]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("the double zero is bettable, and only by the name the cloth gives it")
        void doubleZeroIsBettableAsZeroZero() throws Exception {
            String token = token(guestSession());

            JsonNode result = perform(postJson("/api/games/roulette/spin", """
                    {"bets":[{"type":"STRAIGHT","selection":"00","amount":1.00}]}
                    """, token));
            assertThat(result.get("bets").get(0).get("selection").asText()).isEqualTo("00");

            // 37 is how the double zero is held internally. It must not be a way to bet it.
            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"STRAIGHT","selection":"37","amount":1.00}]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("the five-number bet is accepted and paid at 6 to 1")
        void topLineIsAcceptedAndPaidAtSixToOne() throws Exception {
            String token = token(guestSession());

            for (int spin = 0; spin < 60; spin++) {
                JsonNode result = perform(postJson("/api/games/roulette/spin", """
                        {"bets":[{"type":"TOP_LINE","selection":"0,00,1,2,3","amount":1.00}]}
                        """, token));

                if (result.get("bets").get(0).get("won").asBoolean()) {
                    assertThat(result.get("pocket").asText()).isIn("0", "00", "1", "2", "3");
                    assertThat(result.get("totalPayout").decimalValue())
                            .isEqualByComparingTo("7.00");
                }
            }
        }

        @Test
        @DisplayName("the European basket is refused: it is not a bet on this cloth")
        void europeanBasketRefused() throws Exception {
            String token = token(guestSession());

            // 0-1-2-3 paid 8:1 on a single-zero cloth. Here those pockets are the five-number
            // bet at 6:1, so honouring the old shape would overpay it.
            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"CORNER","selection":"0,1,2,3","amount":10.00}]}
                            """, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a player may cover as many spaces as they can afford")
        void noCapOnHowManySpacesAreCovered() throws Exception {
            String token = token(guestSession());

            // Every number on the cloth at once: 38 bets, well past the 20 that used to be the
            // rule. A guest holds 10,000, so 38 is affordable and must be allowed.
            var bets = new StringBuilder();
            for (int n = 0; n <= 36; n++) {
                bets.append(bets.isEmpty() ? "" : ",")
                        .append("{\"type\":\"STRAIGHT\",\"selection\":\"").append(n)
                        .append("\",\"amount\":1.00}");
            }
            bets.append(",{\"type\":\"STRAIGHT\",\"selection\":\"00\",\"amount\":1.00}");

            JsonNode result = perform(postJson(
                    "/api/games/roulette/spin", "{\"bets\":[" + bets + "]}", token));

            assertThat(result.get("bets")).hasSize(38);
            assertThat(result.get("totalStaked").decimalValue()).isEqualByComparingTo("38.00");
            // Every pocket is covered, so exactly one bet wins and pays 35 plus its stake.
            assertThat(result.get("totalPayout").decimalValue()).isEqualByComparingTo("36.00");
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
        @DisplayName("a forged spin with an absurd number of bets is refused")
        void tooManyBetsRefused() throws Exception {
            String token = token(guestSession());

            // There is no table rule on how many spaces a player covers any more, so this is
            // only the guard against an unbounded array. It sits far above anything the cloth
            // can produce, which is why it takes a forged request to reach it.
            StringBuilder bets = new StringBuilder();
            for (int i = 0; i < 513; i++) {
                if (i > 0) {
                    bets.append(",");
                }
                bets.append("{\"type\":\"STRAIGHT\",\"selection\":\"17\",\"amount\":1.00}");
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
                    assertThat(result.get("pocket").asText()).isEqualTo("17");
                    assertThat(payout).isEqualByComparingTo("36.00");
                } else {
                    assertThat(result.get("pocket").asText()).isNotEqualTo("17");
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
        @DisplayName("a four-hand deal takes four stakes and returns four hands")
        void multiHandDealStakesEveryBox() throws Exception {
            String token = token(guestSession());

            JsonNode round = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 25.00, "hands": 4}
                    """, token));

            assertThat(round.get("hands")).hasSize(4);
            assertThat(round.get("totalStaked").decimalValue()).isEqualByComparingTo("100.00");
            for (JsonNode hand : round.get("hands")) {
                assertThat(hand.get("bet").decimalValue()).isEqualByComparingTo("25.00");
                assertThat(hand.get("cards")).hasSize(2);
            }
            // 10,000 opening balance less the four stakes, before anything is paid back.
            if (!round.get("settled").asBoolean()) {
                assertThat(round.get("balance").decimalValue()).isEqualByComparingTo("9900.00");
            }
        }

        @Test
        @DisplayName("omitting the hand count deals a single box")
        void handCountDefaultsToOne() throws Exception {
            String token = token(guestSession());

            JsonNode round = perform(postJson("/api/games/blackjack/deal", """
                    {"bet": 10.00}
                    """, token));

            assertThat(round.get("hands")).hasSize(1);
            assertThat(round.get("totalStaked").decimalValue()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("more hands than the table allows is refused")
        void tooManyHandsRefused() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/games/blackjack/deal", """
                            {"bet": 10.00, "hands": 5}
                            """, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("At most 4 hands."));
        }

        @Test
        @DisplayName("a player who cannot cover every box is dealt nothing at all")
        void underfundedMultiHandDealIsRefusedWhole() throws Exception {
            // A new player holds 100.00; four boxes at 40.00 needs 160.00.
            String token = token(signUp("multihand_player", "correct-horse-9"));

            mvc.perform(postJson("/api/games/blackjack/deal", """
                            {"bet": 40.00, "hands": 4}
                            """, token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Not enough money."));

            // Nothing was taken and no round was left open.
            JsonNode me = perform(getAs("/api/me", token));
            assertThat(me.get("balance").decimalValue()).isEqualByComparingTo("100.00");
            mvc.perform(getAs("/api/games/blackjack/current", token))
                    .andExpect(status().isNotFound());
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
