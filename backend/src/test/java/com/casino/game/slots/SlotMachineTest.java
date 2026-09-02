package com.casino.game.slots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casino.game.common.Money;
import com.casino.game.common.SeededRandomSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotMachineTest {

    private static final BigDecimal BET = Money.of("1.00");

    @Test
    @DisplayName("a spin shows three symbols per reel, centred on the stop that landed")
    void spinShowsTheWholeWindow() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(1L));

        SpinResult result = machine.spin(BET, 5);

        assertThat(result.stops()).hasSize(3).allSatisfy(stop ->
                assertThat(stop).isBetween(0, SlotPaytable.REEL_STRIP.size() - 1));
        assertThat(result.window()).hasSize(3).allSatisfy(reel -> assertThat(reel).hasSize(3));

        // The centre row must be the symbol at the reported stop, or the animation lies.
        int size = SlotPaytable.REEL_STRIP.size();
        for (int reel = 0; reel < 3; reel++) {
            int stop = result.stops().get(reel);
            assertThat(result.window().get(reel)).containsExactly(
                    SlotPaytable.REEL_STRIP.get(Math.floorMod(stop - 1, size)),
                    SlotPaytable.REEL_STRIP.get(stop),
                    SlotPaytable.REEL_STRIP.get(Math.floorMod(stop + 1, size)));
        }
    }

    @Test
    @DisplayName("the window wraps around the ends of the strip rather than running out")
    void windowWrapsAroundTheStrip() {
        int last = SlotPaytable.REEL_STRIP.size() - 1;

        assertThat(SlotPaytable.windowAt(0)).containsExactly(
                SlotPaytable.REEL_STRIP.get(last),
                SlotPaytable.REEL_STRIP.get(0),
                SlotPaytable.REEL_STRIP.get(1));
        assertThat(SlotPaytable.windowAt(last)).containsExactly(
                SlotPaytable.REEL_STRIP.get(last - 1),
                SlotPaytable.REEL_STRIP.get(last),
                SlotPaytable.REEL_STRIP.get(0));
    }

    @Test
    @DisplayName("credits light the lines in cabinet order: centre, then rows, then diagonals")
    void creditsLightLinesInOrder() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(7L));

        assertThat(machine.spin(BET, 1).lines()).extracting(LineResult::payline)
                .containsExactly(SlotPayline.MIDDLE);
        assertThat(machine.spin(BET, 3).lines()).extracting(LineResult::payline)
                .containsExactly(SlotPayline.MIDDLE, SlotPayline.TOP, SlotPayline.BOTTOM);
        assertThat(machine.spin(BET, 5).lines()).extracting(LineResult::payline)
                .containsExactly(SlotPayline.MIDDLE, SlotPayline.TOP, SlotPayline.BOTTOM,
                        SlotPayline.DIAGONAL_DOWN, SlotPayline.DIAGONAL_UP);
    }

    @Test
    @DisplayName("each line reads its own row on each reel")
    void linesReadTheirOwnRows() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(3L));

        SpinResult result = machine.spin(BET, 5);

        for (LineResult line : result.lines()) {
            for (int reel = 0; reel < 3; reel++) {
                assertThat(line.symbols().get(reel))
                        .isEqualTo(result.window().get(reel).get(line.payline().rowOnReel(reel)));
            }
        }
    }

    @Test
    @DisplayName("the stake is the bet on every lit line, and the payout is the lines added up")
    void stakeAndPayoutFollowTheCredits() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(99L));

        for (int credits : List.of(1, 3, 5)) {
            for (int i = 0; i < 200; i++) {
                SpinResult result = machine.spin(Money.of("2.50"), credits);

                assertThat(result.totalStaked())
                        .isEqualByComparingTo(Money.of("2.50").multiply(BigDecimal.valueOf(credits)));
                assertThat(result.lines()).hasSize(credits);

                BigDecimal expected = result.lines().stream()
                        .map(LineResult::payout)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(result.payout()).isEqualByComparingTo(expected);
                assertThat(result.net())
                        .isEqualByComparingTo(expected.subtract(result.totalStaked()));
            }
        }
    }

    @Test
    @DisplayName("only the lines the player lit are ever scored")
    void unlitLinesPayNothing() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(20260901L));

        for (int i = 0; i < 5_000; i++) {
            SpinResult result = machine.spin(BET, 1);

            assertThat(result.lines()).hasSize(1);
            assertThat(result.lines().get(0).payline()).isEqualTo(SlotPayline.MIDDLE);
        }
    }

    @Test
    @DisplayName("a losing spin returns nothing and costs exactly the total staked")
    void losingSpinCostsTheStake() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(5L));

        SpinResult loss = null;
        for (int i = 0; i < 500 && loss == null; i++) {
            SpinResult result = machine.spin(BET, 3);
            if (!result.isWin()) {
                loss = result;
            }
        }

        assertThat(loss).isNotNull();
        assertThat(loss.payout()).isEqualByComparingTo("0.00");
        assertThat(loss.net()).isEqualByComparingTo("-3.00");
        assertThat(loss.describe()).isEqualTo("No win");
    }

    @Test
    @DisplayName("the return is the same whatever the credits, since every line costs the same")
    void observedReturnIsUnchangedByCredits() {
        for (int credits : List.of(1, 3, 5)) {
            SlotMachine machine = new SlotMachine(new SeededRandomSource(20260826L));
            BigDecimal wagered = BigDecimal.ZERO;
            BigDecimal returned = BigDecimal.ZERO;

            for (int i = 0; i < 120_000; i++) {
                SpinResult result = machine.spin(BET, credits);
                wagered = wagered.add(result.totalStaked());
                returned = returned.add(result.payout());
            }

            double observed = returned.doubleValue() / wagered.doubleValue();
            assertThat(observed)
                    .as("return on %d credits", credits)
                    .isCloseTo(0.96, org.assertj.core.data.Offset.offset(0.03));
        }
    }

    @Test
    @DisplayName("every reel lands on every stop over enough spins, so no stop is unreachable")
    void allStopsAreReachable() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(11L));
        var seen = new java.util.HashSet<Integer>();

        for (int i = 0; i < 20_000; i++) {
            seen.addAll(machine.spin(BET, 1).stops());
        }

        assertThat(seen).hasSize(SlotPaytable.REEL_STRIP.size());
    }

    @Test
    @DisplayName("credits outside the machine's line count are refused")
    void creditsAreBounded() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(2L));

        assertThatThrownBy(() -> machine.spin(BET, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credits");
        assertThatThrownBy(() -> machine.spin(BET, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credits");
    }
}
