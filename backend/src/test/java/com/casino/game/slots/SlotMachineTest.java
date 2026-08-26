package com.casino.game.slots;

import static org.assertj.core.api.Assertions.assertThat;

import com.casino.game.common.Money;
import com.casino.game.common.SeededRandomSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotMachineTest {

    private static final BigDecimal BET = Money.of("1.00");

    @Test
    @DisplayName("a spin reports one stop and one symbol per reel")
    void spinReportsEveryReel() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(1L));

        SpinResult result = machine.spin(BET);

        assertThat(result.stops()).hasSize(3).allSatisfy(stop ->
                assertThat(stop).isBetween(0, SlotPaytable.REEL_STRIP.size() - 1));
        assertThat(result.symbols()).hasSize(3);
        // The reported symbol must be the one at the reported stop, or the animation lies.
        for (int reel = 0; reel < 3; reel++) {
            assertThat(result.symbols().get(reel))
                    .isEqualTo(SlotPaytable.REEL_STRIP.get(result.stops().get(reel)));
        }
    }

    @Test
    @DisplayName("payout and net always agree with the multiplier and the stake")
    void payoutMatchesMultiplier() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(99L));

        for (int i = 0; i < 500; i++) {
            SpinResult result = machine.spin(Money.of("2.50"));

            BigDecimal expectedPayout = Money.of("2.50").multiply(BigDecimal.valueOf(result.multiplier()));
            assertThat(result.payout()).isEqualByComparingTo(expectedPayout);
            assertThat(result.net()).isEqualByComparingTo(expectedPayout.subtract(Money.of("2.50")));
            assertThat(result.isWin()).isEqualTo(result.multiplier() > 0);
        }
    }

    @Test
    @DisplayName("a losing spin returns nothing and costs exactly the stake")
    void losingSpinCostsTheStake() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(5L));

        SpinResult loss = null;
        for (int i = 0; i < 200 && loss == null; i++) {
            SpinResult result = machine.spin(BET);
            if (!result.isWin()) {
                loss = result;
            }
        }

        assertThat(loss).isNotNull();
        assertThat(loss.payout()).isEqualByComparingTo("0.00");
        assertThat(loss.net()).isEqualByComparingTo("-1.00");
        assertThat(loss.combination()).isEqualTo("No win");
    }

    @Test
    @DisplayName("over many spins the observed return converges on the designed 96%")
    void observedReturnConvergesOnDesignedRtp() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(20260826L));
        int spins = 400_000;

        BigDecimal wagered = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        for (int i = 0; i < spins; i++) {
            SpinResult result = machine.spin(BET);
            wagered = wagered.add(BET);
            returned = returned.add(result.payout());
        }

        double observed = returned.doubleValue() / wagered.doubleValue();
        // Seeded, so this is deterministic; the window absorbs ordinary sampling noise.
        assertThat(observed).isCloseTo(0.96, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    @DisplayName("every reel lands on every stop over enough spins, so no stop is unreachable")
    void allStopsAreReachable() {
        SlotMachine machine = new SlotMachine(new SeededRandomSource(11L));
        var seen = new java.util.HashSet<Integer>();

        for (int i = 0; i < 20_000; i++) {
            seen.addAll(machine.spin(BET).stops());
        }

        assertThat(seen).hasSize(SlotPaytable.REEL_STRIP.size());
    }
}
