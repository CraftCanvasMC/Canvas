package io.canvasmc.canvas.world.weather;

import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

public class WeatherServer {

    private static final float LEVEL_STEP = 0.01f;

    public void tickRegion(ServerLevel level, RegionizedWorldData worldData) {
        final RegionizedWorldData.RegionalWeatherState st = worldData.weatherRegional;
        if (st == null) return;

        initIfNeeded(level, worldData, st);
        tickTimers(level, st);

        st.oRainLevel = st.rainLevel;
        st.oThunderLevel = st.thunderLevel;

        st.rainLevel = approach(st.rainLevel, st.raining ? 1.0f : 0.0f, LEVEL_STEP);
        st.thunderLevel = approach(st.thunderLevel, st.thundering ? 1.0f : 0.0f, LEVEL_STEP);

        final var players = worldData.getLocalPlayers();
        if (!players.isEmpty()) {
            for (final ServerPlayer player : players) {
                player.canvas$syncRegionalWeather(st);
            }
        }
    }

    private void initIfNeeded(ServerLevel level, RegionizedWorldData worldData, RegionizedWorldData.RegionalWeatherState st) {
        if (st.initialized) return;

        st.regionSeed = level.getSeed() ^ worldData.regionData.id;
        st.raining = level.isRaining();
        st.thundering = level.isThundering();
        st.clearTime = 6000;
        st.rainTime = 6000;
        st.thunderTime = 6000;
        st.rainLevel = st.raining ? 1.0f : 0.0f;
        st.thunderLevel = st.thundering ? 1.0f : 0.0f;
        st.initialized = true;
    }

    private void tickTimers(ServerLevel level, RegionizedWorldData.RegionalWeatherState st) {
        final RandomSource rng = level.getRandom();

        if (!st.raining) {
            if (--st.clearTime <= 0) {
                st.raining = true;
                st.rainTime = randomRainDuration(rng);
                if (rng.nextInt(100) < 30) {
                    st.thundering = true;
                    st.thunderTime = randomThunderDuration(rng);
                }
            }
        } else {
            if (--st.rainTime <= 0) {
                st.raining = false;
                st.thundering = false;
                st.clearTime = randomClearDuration(rng);
            }
        }

        if (st.thundering) {
            if (--st.thunderTime <= 0) {
                st.thundering = false;
                st.thunderTime = randomThunderDuration(rng);
            }
        }
    }

    private int randomClearDuration(RandomSource rng) {
        return 6000 + rng.nextInt(12000);
    }

    private int randomRainDuration(RandomSource rng) {
        return 6000 + rng.nextInt(12000);
    }

    private int randomThunderDuration(RandomSource rng) {
        return 3000 + rng.nextInt(6000);
    }

    private float approach(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        if (current > target) return Math.max(current - step, target);
        return current;
    }
}
