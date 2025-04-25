package io.canvasmc.canvas;

import org.jetbrains.annotations.ApiStatus;

public class TickTimes {
    private long[] times;
    private long intervalNs;

    public TickTimes(int length) {
        this.times = new long[length];
        this.intervalNs = ThreadedBukkitServer.getInstance().getScheduler().getTimeBetweenTicks();
    }

    /**
     * This is for internal use to update timings, don't use
     */
    @ApiStatus.Internal
    public void add(int index, long time) {
        times[index % times.length] = time;
    }

    public long[] getTimes() {
        return times.clone();
    }

    /**
     * Gets the average mspt in the last interval
     * @return average mspt
     */
    public double getAverage() {
        long total = 0L;
        for (long value : times) {
            total += value;
        }
        return ((double) total / (double) times.length) * 1.0E-6D;
    }

    /**
     * Gets the calculated thread utilization in the last interval
     * @return thread util
     */
    public double getUtilization() {
        long totalExecutionTime = 0L;
        for (long time : times) {
            totalExecutionTime += time;
        }
        long totalElapsedTime = times.length * intervalNs;
        return ((double) totalExecutionTime / totalElapsedTime) * 100;
    }

    /**
     * Resets the tick times. Used primarily when changing the tick rate
     */
    public void reset() {
        this.times = new long[times.length];
        this.intervalNs = ThreadedBukkitServer.getInstance().getScheduler().getTimeBetweenTicks();
    }
}
