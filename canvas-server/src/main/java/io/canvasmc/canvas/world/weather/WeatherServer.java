package io.canvasmc.canvas.world.weather;

import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Per-region weather engine, called each tick from the region's tick loop.
 * Advances the clear/rain/thunder timers, fades the intensity levels and
 * syncs the result to the players inside the region.
 */
@NullMarked
public class WeatherServer {

    /**
     * Level change per tick (~5s fade from 0 to 1).
     */
    private static final float LEVEL_STEP = 0.01f;

    public void tickRegion(ServerLevel level, RegionizedWorldData worldData) {
        final RegionizedWorldData.@Nullable RegionalWeatherState weatherState = worldData.weatherRegional;
        if (weatherState == null) return;

        initIfNeeded(level, worldData, weatherState);
        tickTimers(level, weatherState);

        // keep previous levels for client-side interpolation, then fade toward target
        weatherState.oRainLevel = weatherState.rainLevel;
        weatherState.oThunderLevel = weatherState.thunderLevel;
        weatherState.rainLevel = approach(weatherState.rainLevel, weatherState.raining ? 1.0f : 0.0f, LEVEL_STEP);
        weatherState.thunderLevel = approach(weatherState.thunderLevel, weatherState.thundering ? 1.0f : 0.0f, LEVEL_STEP);

        final var players = worldData.getLocalPlayers();
        if (!players.isEmpty()) {
            for (final ServerPlayer player : players) {
                player.canvas$syncRegionalWeather(weatherState);
            }
        }
    }

    /**
     * Seeds the state from the world's current weather on first tick.
     */
    private void initIfNeeded(ServerLevel level, RegionizedWorldData worldData, RegionizedWorldData.RegionalWeatherState weatherState) {
        if (weatherState.initialized) return;

        weatherState.regionSeed = level.getSeed() ^ worldData.regionData.id;

        weatherState.raining = level.isRaining();
        weatherState.thundering = level.isThundering();

        weatherState.clearTime = 6000;
        weatherState.rainTime = 6000;
        weatherState.thunderTime = 6000;

        weatherState.rainLevel = weatherState.raining ? 1.0f : 0.0f;
        weatherState.thunderLevel = weatherState.thundering ? 1.0f : 0.0f;

        weatherState.initialized = true;
    }

    /**
     * Weather cycle: clear -> rain (30% chance of thunder) -> clear, with
     * random durations rolled when each timer expires.
     */
    private void tickTimers(ServerLevel level, RegionizedWorldData.RegionalWeatherState weatherState) {
        final RandomSource rng = level.getRandom();

        if (!weatherState.raining) {
            if (--weatherState.clearTime <= 0) {
                weatherState.raining = true;
                weatherState.rainTime = randomRainDuration(rng);

                if (rng.nextInt(100) < 30) {
                    weatherState.thundering = true;
                    weatherState.thunderTime = randomThunderDuration(rng);
                }
            }
        } else {
            if (--weatherState.rainTime <= 0) {
                weatherState.raining = false;
                weatherState.thundering = false;
                weatherState.clearTime = randomClearDuration(rng);
            }
        }

        if (weatherState.thundering) {
            if (--weatherState.thunderTime <= 0) {
                weatherState.thundering = false;
                weatherState.thunderTime = randomThunderDuration(rng);
            }
        }
    }

    /**
     * 5 to 15 minutes.
     */
    private int randomClearDuration(RandomSource rng) {
        return 6000 + rng.nextInt(12000);
    }

    /**
     * 5 to 15 minutes.
     */
    private int randomRainDuration(RandomSource rng) {
        return 6000 + rng.nextInt(12000);
    }

    /**
     * 2.5 to 7.5 minutes.
     */
    private int randomThunderDuration(RandomSource rng) {
        return 3000 + rng.nextInt(6000);
    }

    /**
     * Moves {@code current} toward {@code target} by at most {@code step}.
     */
    private float approach(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        if (current > target) return Math.max(current - step, target);
        return current;
    }
}
