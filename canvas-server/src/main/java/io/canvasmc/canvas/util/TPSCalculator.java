package io.canvasmc.canvas.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TPSCalculator {
    public static final int MAX_TPS = 20;
    public static final int FULL_TICK = 50;
    private static final int historyLimit = 40;
    private final List<Double> tpsHistory = new CopyOnWriteArrayList<>();
    public Long lastTick;
    public Long currentTick;
    private double allMissedTicks = 0;

    public TPSCalculator() {
    }

    public void doTick() {
        if (currentTick != null) {
            lastTick = currentTick;
        }

        currentTick = System.currentTimeMillis();
        addToHistory(getTPS());
        clearMissedTicks();
        missedTick();
    }

    private void addToHistory(double tps) {
        if (tpsHistory.size() >= historyLimit) {
            tpsHistory.remove(0);
        }

        tpsHistory.add(tps);
    }

    public long getMSPT() {
        return currentTick - lastTick;
    }

    public double getAverageTPS() {
        return tpsHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.1);
    }

    public double getTPS() {
        if (lastTick == null) return -1;
        if (getMSPT() <= 0) return 0.1;

        double tps = 1000 / (double) getMSPT();
        return tps > MAX_TPS ? MAX_TPS : tps;
    }

    public void missedTick() {
        if (lastTick == null) return;

        long mspt = getMSPT() <= 0 ? 50 : getMSPT();
        double missedTicks = (mspt / (double) FULL_TICK) - 1;
        allMissedTicks += missedTicks <= 0 ? 0 : missedTicks;
    }

    public double getMostAccurateTPS() {
        return getTPS() > getAverageTPS() ? getAverageTPS() : getTPS();
    }

    public double getAllMissedTicks() {
        return allMissedTicks;
    }

    public int applicableMissedTicks() {
        return (int) Math.floor(allMissedTicks);
    }

    public void clearMissedTicks() {
        allMissedTicks -= applicableMissedTicks();
    }

    public void resetMissedTicks() {
        allMissedTicks = 0;
    }
}
