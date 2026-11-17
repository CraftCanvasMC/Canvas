package io.canvasmc.amethyst.sampler;

import java.time.Duration;

/**
 * This is what async-profiler records. CPU is sampled on every thread, wall only on game threads
 *
 * @param cpuInterval
 *     the CPU time between CPU samples
 * @param wallInterval
 *     elapsed time between wall samples
 * @param allocation
 *     whether to also do allocation profiling
 * @param allocationInterval
 *     bytes allocated between samples
 * @param lock
 *     whether to also do lock profiling
 * @param lockThreshold
 *     the shortest lock wait that's sampled
 *
 * @author dueris
 */
public record SamplerSettings(
    Duration cpuInterval,
    Duration wallInterval,
    boolean allocation,
    long allocationInterval,
    boolean lock,
    Duration lockThreshold
) {
    // SHOULD be the same as spark defaults i think
    public static final SamplerSettings DEFAULT = new SamplerSettings(
        Duration.ofMillis(4L),
        Duration.ofMillis(4L),
        false,
        524287L, // 512KiB
        false,
        Duration.ofNanos(10_000L)
    );
}
