package com.casino.game.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("amounts are always normalised to 2 decimal places")
    void scalesToTwoPlaces() {
        assertThat(Money.of("5")).isEqualByComparingTo("5.00");
        assertThat(Money.of("5").scale()).isEqualTo(2);
        assertThat(Money.scaled(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("a 3:2 blackjack payout on an odd stake rounds cleanly")
    void blackjackPayoutRounds() {
        // 3:2 on 5.05 is 7.575, which must land on a real cent.
        assertThat(Money.multiply(Money.of("5.05"), 1.5)).isEqualByComparingTo("7.58");
        assertThat(Money.multiply(Money.of("10.00"), 1.5)).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("repeated addition does not drift, unlike binary floating point")
    void repeatedAdditionDoesNotDrift() {
        BigDecimal total = Money.ZERO;
        for (int i = 0; i < 1000; i++) {
            total = Money.scaled(total.add(Money.of("0.10")));
        }

        assertThat(total).isEqualByComparingTo("100.00");

        // The same loop in a double is visibly wrong, which is why money is never a double here.
        double drifting = 0.0;
        for (int i = 0; i < 1000; i++) {
            drifting += 0.1;
        }
        assertThat(drifting).isNotEqualTo(100.0);
    }

    @Test
    @DisplayName("isPositive rejects zero, negatives and null")
    void isPositive() {
        assertThat(Money.isPositive(Money.of("0.01"))).isTrue();
        assertThat(Money.isPositive(Money.ZERO)).isFalse();
        assertThat(Money.isPositive(Money.of("-1.00"))).isFalse();
        assertThat(Money.isPositive(null)).isFalse();
    }
}
