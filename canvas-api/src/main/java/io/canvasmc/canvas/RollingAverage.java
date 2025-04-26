package io.canvasmc.canvas;

import io.canvasmc.canvas.scheduler.MultithreadedTickScheduler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class RollingAverage {
    public static final long SEC_IN_NANO = 1_000_000_000;
    private final int size;
    private long time;
    private java.math.BigDecimal total;
    private int index = 0;
    private java.math.BigDecimal[] samples;
    private long[] times;

    public RollingAverage(int size) {
        this.size = size;
        this.time = size * SEC_IN_NANO;
        this.total = dec(ThreadedBukkitServer.getInstance().getScheduler().getTickRate()).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
        this.samples = new java.math.BigDecimal[size];
        this.times = new long[size];
        for (int i = 0; i < size; i++) {
            this.samples[i] = dec(ThreadedBukkitServer.getInstance().getScheduler().getTickRate());
            this.times[i] = SEC_IN_NANO;
        }
    }

    @Contract(value = "_ -> new", pure = true)
    private static java.math.@NotNull BigDecimal dec(long t) {
        return new java.math.BigDecimal(t);
    }

    /**
     * This is for internal use to update timings, don't use
     */
    @ApiStatus.Internal
    public void add(java.math.@NotNull BigDecimal x, long t) {
        java.math.BigDecimal maxTickRate = dec(ThreadedBukkitServer.getInstance().getScheduler().getTickRate());
        if (x.compareTo(maxTickRate) > 0) {
            x = maxTickRate; // clamp to tick rate
        }

        time -= times[index];
        total = total.subtract(samples[index].multiply(dec(times[index])));
        samples[index] = x;
        times[index] = t;
        time += t;
        total = total.add(x.multiply(dec(t)));
        if (++index == size) {
            index = 0;
        }
    }

    /**
     * Gets the TPS in the last interval
     * @return TPS average
     */
    public double getAverage() {
        return total.divide(dec(time), 30, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Resets the times and samples. Used when updating the tick rate of the server.
     */
    public void reset() {
        this.total = dec(ThreadedBukkitServer.getInstance().getScheduler().getTickRate()).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
        this.samples = new java.math.BigDecimal[size];
        this.times = new long[size];
        for (int i = 0; i < size; i++) {
            this.samples[i] = dec(ThreadedBukkitServer.getInstance().getScheduler().getTickRate());
            this.times[i] = SEC_IN_NANO;
        }
    }
}
