package io.canvasmc.amethyst;

import java.time.Instant;

/**
 * When a profile starts, on both clocks. Every {@code *_us} time in a report is relative to the start nanos
 *
 * @param startNano
 *     nanotime at the start
 * @param startEpochNanos
 *     wall clock at the start, in nanos since the epoch
 *
 * @author dueris
 */
public record ProfileClock(long startNano, long startEpochNanos) {

    public static ProfileClock now() {
        final Instant now = Instant.now();
        return new ProfileClock(System.nanoTime(), now.getEpochSecond() * 1_000_000_000L + now.getNano());
    }

    /// @return microseconds since the start
    public long sinceStartUs(final long nanoTime) {
        return (nanoTime - startNano) / 1000L;
    }
}
