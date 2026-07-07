package io.canvasmc.canvas.world.weather;

import com.google.common.base.Preconditions;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads and mutates the per-region weather state stored in {@link RegionizedWorldData}.
 * Must be called from the tick thread owning the region at the given chunk coordinates.
 */
@NullMarked
public final class WeatherControllerImpl implements WeatherController {

    /**
     * Applies levels immediately (current + old), bypassing the progressive fade.
     */
    private static void setLevelsInstant(final RegionizedWorldData.RegionalWeatherState weatherState, final float rain, final float thunder) {
        try {
            weatherState.oRainLevel = rain;
            weatherState.oThunderLevel = thunder;
        } catch (Throwable ignored) {

        }

        weatherState.rainLevel = rain;
        weatherState.thunderLevel = thunder;
    }

    /**
     * Sets the region's weather. If the state was never touched, it is first seeded
     * from the world's global weather. A positive {@code durationTicks} holds the
     * weather that long; otherwise the current cycle continues. {@code instantLevels}
     * skips the client-side fade and applies intensities immediately.
     */
    @Override
    public void setRegionalWeather(World world, int chunkX, int chunkZ, WeatherType type, int durationTicks, boolean instantLevels) {
        Preconditions.checkNotNull(world, "world cannot be null");
        Preconditions.checkNotNull(type, "type cannot be null");

        final ServerLevel level = ((CraftWorld) world).getHandle();
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(level, chunkX, chunkZ, "Cannot retrieve chunk asynchronously");

        final RegionizedWorldData worldData = level.getCurrentWorldData();
        final RegionizedWorldData.@Nullable RegionalWeatherState weatherState = worldData.weatherRegional;

        if (weatherState == null) {
            return; // per-region weather disabled
        }

        if (!weatherState.initialized) {
            // seed from the world's current weather, then evolve independently
            weatherState.regionSeed = level.getSeed() ^ worldData.regionData.id;

            weatherState.raining = level.isRaining();
            weatherState.thundering = level.isThundering();

            weatherState.clearTime = 6000;
            weatherState.rainTime = 6000;
            weatherState.thunderTime = 6000;

            weatherState.rainLevel = weatherState.raining ? 1.0f : 0.0f;
            weatherState.thunderLevel = weatherState.thundering ? 1.0f : 0.0f;

            try {
                weatherState.oRainLevel = weatherState.rainLevel;
                weatherState.oThunderLevel = weatherState.thunderLevel;
            } catch (Throwable ignored) {
            }

            weatherState.initialized = true;
        }

        final int duration = durationTicks > 0 ? durationTicks : -1;

        switch (type) {
            case CLEAR -> {
                weatherState.raining = false;
                weatherState.thundering = false;

                // if duration provided: stay clear that long
                if (duration > 0) {
                    weatherState.clearTime = duration;
                    weatherState.rainTime = 0;
                    weatherState.thunderTime = 0;
                } else {
                    weatherState.clearTime = Math.max(weatherState.clearTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(weatherState, 0.0f, 0.0f);
                }
            }
            case RAIN -> {
                weatherState.raining = true;
                weatherState.thundering = false;

                if (duration > 0) {
                    weatherState.rainTime = duration;
                    weatherState.thunderTime = 0;
                    weatherState.clearTime = 0;
                } else {
                    weatherState.rainTime = Math.max(weatherState.rainTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(weatherState, 1.0f, 0.0f);
                }
            }
            case THUNDER -> {
                weatherState.raining = true; // thunder implies rain
                weatherState.thundering = true;

                if (duration > 0) {
                    weatherState.rainTime = duration;
                    weatherState.thunderTime = duration;
                    weatherState.clearTime = 0;
                } else {
                    weatherState.rainTime = Math.max(weatherState.rainTime, 1);
                    weatherState.thunderTime = Math.max(weatherState.thunderTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(weatherState, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Returns a snapshot of the region's weather, or {@code null} if per-region
     * weather is disabled for this world.
     */
    @Override
    public @Nullable RegionalWeatherSnapshot getRegionalWeather(World world, int chunkX, int chunkZ) {
        Preconditions.checkNotNull(world, "world cannot be null");
        final ServerLevel level = ((CraftWorld) world).getHandle();
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(level, chunkX, chunkZ, "Cannot retrieve chunk asynchronously");

        final RegionizedWorldData worldData = level.getCurrentWorldData();
        final RegionizedWorldData.@Nullable RegionalWeatherState weatherState = worldData.weatherRegional;
        if (weatherState == null) {
            return null;
        }

        return new RegionalWeatherSnapshot(
            worldData.regionData.id,
            weatherState.raining,
            weatherState.thundering,
            weatherState.clearTime,
            weatherState.rainTime,
            weatherState.thunderTime,
            weatherState.rainLevel,
            weatherState.thunderLevel
        );
    }


}
