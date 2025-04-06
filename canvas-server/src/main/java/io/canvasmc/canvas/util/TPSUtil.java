package io.canvasmc.canvas.util;

import io.canvasmc.canvas.region.ServerRegions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class TPSUtil {
    public static final int MAX_TPS = 20;
    public static final int FULL_TICK = 50;

    public static float tt20(float ticks, boolean limitZero, @Nullable ServerLevel level) {
        float newTicks = (float) rawTT20(ticks, level);

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static int tt20(int ticks, boolean limitZero, @Nullable ServerLevel level) {
        int newTicks = (int) Math.ceil(rawTT20(ticks, level));

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static double tt20(double ticks, boolean limitZero, @Nullable ServerLevel level) {
        double newTicks = rawTT20(ticks, level);

        if (limitZero) return newTicks > 0 ? newTicks : 1;
        else return newTicks;
    }

    public static double rawTT20(double ticks, @Nullable ServerLevel level) {
        return ticks == 0 ? 0 : ticks * (level == null ? MinecraftServer.getServer().tpsCalculator.getMostAccurateTPS() : ServerRegions.getTickData(level).tpsCalculator.getMostAccurateTPS()) / MAX_TPS; // Canvas - Threaded Regions
    }
}
