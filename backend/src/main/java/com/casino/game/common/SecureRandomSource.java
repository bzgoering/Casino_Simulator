package com.casino.game.common;

import java.security.SecureRandom;

/**
 * Cryptographically strong {@link RandomSource} used for all real game outcomes.
 *
 * <p>{@link SecureRandom} is thread-safe, so a single instance is shared across
 * concurrent rounds.
 */
public final class SecureRandomSource implements RandomSource {

    private final SecureRandom random = new SecureRandom();

    @Override
    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }
}
