package com.casino.game.common;

/**
 * Source of randomness for every game outcome in the platform.
 *
 * <p>All production wiring uses {@link SecureRandomSource}, which is backed by
 * {@link java.security.SecureRandom}. The interface exists so tests can inject a
 * deterministic sequence and assert on exact game outcomes: game logic must never
 * reach for {@code Math.random()} or an unseeded {@code Random} directly.
 */
public interface RandomSource {

    /** Uniformly distributed int in {@code [0, boundExclusive)}. */
    int nextInt(int boundExclusive);

    /** Uniformly distributed double in {@code [0.0, 1.0)}. */
    double nextDouble();
}
