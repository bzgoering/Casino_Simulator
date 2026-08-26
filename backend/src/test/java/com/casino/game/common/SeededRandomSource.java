package com.casino.game.common;

import java.util.Random;

/**
 * A reproducible {@link RandomSource} for tests.
 *
 * <p>Uses {@link Random}, which is emphatically not suitable for real play, but is exactly what a
 * test needs: the same seed replays the same shuffle every run, so a failure is reproducible.
 */
public final class SeededRandomSource implements RandomSource {

    private final Random random;

    public SeededRandomSource(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }
}
