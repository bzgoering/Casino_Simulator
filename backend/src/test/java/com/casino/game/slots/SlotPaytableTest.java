package com.casino.game.slots;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The machine's odds, verified by enumerating the entire outcome space.
 *
 * <p>Three reels of 32 stops give 32,768 equally likely combinations, so the return to player is
 * an exact figure rather than an estimate. These tests pin it: any change to a reel strip or a
 * payout that moves the RTP will fail here rather than quietly shipping a different game.
 */
class SlotPaytableTest {

    private static final int STRIP_SIZE = 32;
    private static final long OUTCOMES = (long) STRIP_SIZE * STRIP_SIZE * STRIP_SIZE;

    @Test
    @DisplayName("each reel strip has 32 stops with the intended symbol weights")
    void reelStripHasExpectedWeights() {
        assertThat(SlotPaytable.REEL_STRIP).hasSize(STRIP_SIZE);

        Map<SlotSymbol, Integer> counts = new HashMap<>();
        SlotPaytable.REEL_STRIP.forEach(symbol -> counts.merge(symbol, 1, Integer::sum));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                SlotSymbol.SEVEN, 1,
                SlotSymbol.BAR3, 2,
                SlotSymbol.BAR2, 3,
                SlotSymbol.BAR1, 4,
                SlotSymbol.BELL, 5,
                SlotSymbol.PLUM, 5,
                SlotSymbol.ORANGE, 6,
                SlotSymbol.CHERRY, 6));
    }

    @Test
    @DisplayName("the exact return to player is 96.005%, a realistic house edge of about 4%")
    void returnToPlayerIsNinetySixPercent() {
        long returned = totalReturnedAcrossAllOutcomes();
        double rtp = (double) returned / OUTCOMES;

        assertThat(rtp).isCloseTo(0.96005, org.assertj.core.data.Offset.offset(0.00005));
        // Stated as the invariant that actually matters: the house keeps a small, positive edge.
        assertThat(rtp).isLessThan(1.0).isGreaterThan(0.90);
    }

    @Test
    @DisplayName("the machine pays on 22.4% of spins")
    void hitFrequencyIsRealistic() {
        long winningCombinations = 0;
        for (SlotSymbol a : SlotPaytable.REEL_STRIP) {
            for (SlotSymbol b : SlotPaytable.REEL_STRIP) {
                for (SlotSymbol c : SlotPaytable.REEL_STRIP) {
                    if (SlotPaytable.multiplierFor(a, b, c) > 0) {
                        winningCombinations++;
                    }
                }
            }
        }

        double hitRate = (double) winningCombinations / OUTCOMES;
        assertThat(hitRate).isCloseTo(0.224, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("the top jackpot is the rarest combination on the machine")
    void jackpotIsRarest() {
        // One stop in 32 per reel: 1 in 32,768 spins.
        assertThat(SlotPaytable.multiplierFor(SlotSymbol.SEVEN, SlotSymbol.SEVEN, SlotSymbol.SEVEN))
                .isEqualTo(200);
        assertThat(SlotPaytable.REEL_STRIP.stream().filter(s -> s == SlotSymbol.SEVEN).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cherries pay from the left, and more cherries never pay less")
    void cherriesPayFromTheLeft() {
        int one = SlotPaytable.multiplierFor(SlotSymbol.CHERRY, SlotSymbol.BELL, SlotSymbol.BELL);
        int two = SlotPaytable.multiplierFor(SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.BELL);
        int three = SlotPaytable.multiplierFor(SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY);

        assertThat(one).isEqualTo(2);
        assertThat(two).isEqualTo(6);
        assertThat(three).isEqualTo(10);
        assertThat(one).isLessThan(two);
        assertThat(two).isLessThan(three);

        // A cherry anywhere but the first reel pays nothing on its own.
        assertThat(SlotPaytable.multiplierFor(SlotSymbol.BELL, SlotSymbol.CHERRY, SlotSymbol.CHERRY))
                .isZero();
    }

    @Test
    @DisplayName("mixed bars pay, but less than any pure bar line")
    void mixedBarsPayLessThanPureLines() {
        int mixed = SlotPaytable.multiplierFor(SlotSymbol.BAR1, SlotSymbol.BAR2, SlotSymbol.BAR3);

        assertThat(mixed).isEqualTo(5);
        assertThat(mixed).isLessThan(
                SlotPaytable.multiplierFor(SlotSymbol.BAR1, SlotSymbol.BAR1, SlotSymbol.BAR1));
        assertThat(SlotPaytable.multiplierFor(SlotSymbol.BAR3, SlotSymbol.BAR3, SlotSymbol.BAR3))
                .isGreaterThan(SlotPaytable.multiplierFor(SlotSymbol.BAR1, SlotSymbol.BAR1, SlotSymbol.BAR1));
    }

    @Test
    @DisplayName("rarer symbols always pay at least as much as commoner ones")
    void payoutsTrackRarity() {
        List<SlotSymbol> byIncreasingRarity = List.of(
                SlotSymbol.CHERRY, SlotSymbol.ORANGE, SlotSymbol.PLUM,
                SlotSymbol.BELL, SlotSymbol.BAR1, SlotSymbol.BAR2, SlotSymbol.BAR3, SlotSymbol.SEVEN);

        int previousPayout = 0;
        long previousCount = Long.MAX_VALUE;
        for (SlotSymbol symbol : byIncreasingRarity) {
            long count = SlotPaytable.REEL_STRIP.stream().filter(s -> s == symbol).count();
            int payout = SlotPaytable.multiplierFor(symbol, symbol, symbol);

            assertThat(count).as("%s should be no commoner than the previous symbol", symbol)
                    .isLessThanOrEqualTo(previousCount);
            assertThat(payout).as("%s should pay at least as much as the previous symbol", symbol)
                    .isGreaterThanOrEqualTo(previousPayout);

            previousCount = count;
            previousPayout = payout;
        }
    }

    @Test
    @DisplayName("no combination ever pays a negative amount")
    void noNegativePayouts() {
        for (SlotSymbol a : SlotPaytable.REEL_STRIP) {
            for (SlotSymbol b : SlotPaytable.REEL_STRIP) {
                for (SlotSymbol c : SlotPaytable.REEL_STRIP) {
                    assertThat(SlotPaytable.multiplierFor(a, b, c)).isNotNegative();
                }
            }
        }
    }

    @Test
    @DisplayName("every payline returns exactly the same 96.005%, so lines do not change the odds")
    void everyPaylineHasTheSameReturn() {
        int size = SlotPaytable.REEL_STRIP.size();
        long expected = totalReturnedAcrossAllOutcomes();

        // Enumerate all 32^3 stop combinations and score each payline across the window they
        // produce. A line reads one symbol per reel, each a uniform draw over the same strip, so
        // its return must come out identical to the single-line figure. If a payline were ever
        // defined to read two rows off one reel, this is where it would show up.
        for (SlotPayline payline : SlotPayline.values()) {
            long returned = 0;
            for (int first = 0; first < size; first++) {
                List<SlotSymbol> reelA = SlotPaytable.windowAt(first);
                for (int second = 0; second < size; second++) {
                    List<SlotSymbol> reelB = SlotPaytable.windowAt(second);
                    for (int third = 0; third < size; third++) {
                        List<SlotSymbol> reelC = SlotPaytable.windowAt(third);
                        returned += SlotPaytable.multiplierFor(
                                reelA.get(payline.rowOnReel(0)),
                                reelB.get(payline.rowOnReel(1)),
                                reelC.get(payline.rowOnReel(2)));
                    }
                }
            }

            assertThat(returned).as("total returned on the %s", payline.displayName())
                    .isEqualTo(expected);
            assertThat((double) returned / OUTCOMES)
                    .as("RTP on the %s", payline.displayName())
                    .isCloseTo(0.96005, org.assertj.core.data.Offset.offset(0.00005));
        }
    }

    @Test
    @DisplayName("the five paylines are the three rows and the two diagonals, and nothing else")
    void paylinesAreTheRowsAndDiagonals() {
        assertThat(SlotPayline.values()).hasSize(5);

        // Straight across: one row read on all three reels.
        assertThat(SlotPayline.MIDDLE.rows()).containsExactly(1, 1, 1);
        assertThat(SlotPayline.TOP.rows()).containsExactly(0, 0, 0);
        assertThat(SlotPayline.BOTTOM.rows()).containsExactly(2, 2, 2);
        // Diagonal: a different row on every reel.
        assertThat(SlotPayline.DIAGONAL_DOWN.rows()).containsExactly(0, 1, 2);
        assertThat(SlotPayline.DIAGONAL_UP.rows()).containsExactly(2, 1, 0);

        for (SlotPayline payline : SlotPayline.values()) {
            assertThat(payline.rows()).hasSize(SlotPaytable.REEL_COUNT)
                    .allSatisfy(row -> assertThat(row).isBetween(0, SlotPaytable.ROW_COUNT - 1));
        }
    }

    @Test
    @DisplayName("credits light the centre line first, then the rows, then the diagonals")
    void creditsLightLinesInCabinetOrder() {
        assertThat(SlotPayline.litBy(1)).containsExactly(SlotPayline.MIDDLE);
        assertThat(SlotPayline.litBy(3)).containsExactly(
                SlotPayline.MIDDLE, SlotPayline.TOP, SlotPayline.BOTTOM);
        assertThat(SlotPayline.litBy(5)).containsExactlyElementsOf(List.of(SlotPayline.values()));
    }

    private static long totalReturnedAcrossAllOutcomes() {
        long returned = 0;
        for (SlotSymbol a : SlotPaytable.REEL_STRIP) {
            for (SlotSymbol b : SlotPaytable.REEL_STRIP) {
                for (SlotSymbol c : SlotPaytable.REEL_STRIP) {
                    returned += SlotPaytable.multiplierFor(a, b, c);
                }
            }
        }
        return returned;
    }
}
