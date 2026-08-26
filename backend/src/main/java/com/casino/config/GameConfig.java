package com.casino.config;

import com.casino.game.common.RandomSource;
import com.casino.game.common.SecureRandomSource;
import com.casino.game.roulette.RouletteTable;
import com.casino.game.roulette.RouletteWheel;
import com.casino.game.slots.SlotMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the game engines.
 *
 * <p>The engines are plain objects with no Spring annotations of their own, so they stay unit
 * testable with a seeded {@link RandomSource} and free of framework coupling. A single
 * {@link SecureRandomSource} backs every game; it is thread-safe and shared.
 */
@Configuration
public class GameConfig {

    @Bean
    public RandomSource randomSource() {
        return new SecureRandomSource();
    }

    @Bean
    public SlotMachine slotMachine(RandomSource randomSource) {
        return new SlotMachine(randomSource);
    }

    @Bean
    public RouletteWheel rouletteWheel(RandomSource randomSource) {
        return new RouletteWheel(randomSource);
    }

    @Bean
    public RouletteTable rouletteTable(RouletteWheel wheel) {
        return new RouletteTable(wheel);
    }
}
