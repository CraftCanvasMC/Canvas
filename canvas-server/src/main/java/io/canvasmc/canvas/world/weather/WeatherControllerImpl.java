package io.canvasmc.canvas.world.weather;

import com.google.common.base.Preconditions;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jspecify.annotations.Nullable;

public final class WeatherControllerImpl implements WeatherController {

    @Override
    public void setRegionalWeather(World world, int chunkX, int chunkZ, WeatherType type, int durationTicks, boolean instantLevels) {
        Preconditions.checkNotNull(world, "world cannot be null");
        Preconditions.checkNotNull(type, "type cannot be null");

        final ServerLevel level = ((CraftWorld) world).getHandle();
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(level, chunkX, chunkZ, "Cannot retrieve chunk asynchronously");

        final RegionizedWorldData worldData = level.getCurrentWorldData();
        final RegionizedWorldData.RegionalWeatherState st = worldData.weatherRegional;

        if (st == null) {
            return;
        }

        if (!st.initialized) {
            st.regionSeed = level.getSeed() ^ worldData.regionData.id;

            st.raining = level.isRaining();
            st.thundering = level.isThundering();

            st.clearTime = 6000;
            st.rainTime = 6000;
            st.thunderTime = 6000;

            st.rainLevel = st.raining ? 1.0f : 0.0f;
            st.thunderLevel = st.thundering ? 1.0f : 0.0f;

            try {
                st.oRainLevel = st.rainLevel;
                st.oThunderLevel = st.thunderLevel;
            } catch (Throwable ignored) {
            }

            st.initialized = true;
        }

        final int d = durationTicks > 0 ? durationTicks : -1;

        switch (type) {
            case CLEAR -> {
                st.raining = false;
                st.thundering = false;

                // if duration provided: stay clear that long
                if (d > 0) {
                    st.clearTime = d;
                    st.rainTime = 0;
                    st.thunderTime = 0;
                } else {
                    st.clearTime = Math.max(st.clearTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(st, 0.0f, 0.0f);
                }
            }
            case RAIN -> {
                st.raining = true;
                st.thundering = false;

                if (d > 0) {
                    st.rainTime = d;
                    st.thunderTime = 0;
                    st.clearTime = 0;
                } else {
                    st.rainTime = Math.max(st.rainTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(st, 1.0f, 0.0f);
                }
            }
            case THUNDER -> {
                st.raining = true;
                st.thundering = true;

                if (d > 0) {
                    st.rainTime = d;
                    st.thunderTime = d;
                    st.clearTime = 0;
                } else {
                    st.rainTime = Math.max(st.rainTime, 1);
                    st.thunderTime = Math.max(st.thunderTime, 1);
                }

                if (instantLevels) {
                    setLevelsInstant(st, 1.0f, 1.0f);
                }
            }
        }
    }

    @Override
    public @Nullable RegionalWeatherSnapshot getRegionalWeather(World world, int chunkX, int chunkZ) {
        Preconditions.checkNotNull(world, "world cannot be null");
        final ServerLevel level = ((CraftWorld) world).getHandle();
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(level, chunkX, chunkZ, "Cannot retrieve chunk asynchronously");

        final RegionizedWorldData worldData = level.getCurrentWorldData();
        final RegionizedWorldData.RegionalWeatherState st = worldData.weatherRegional;
        if (st == null) {
            return null;
        }

        return new RegionalWeatherSnapshot(
            worldData.regionData.id,
            st.raining,
            st.thundering,
            st.clearTime,
            st.rainTime,
            st.thunderTime,
            st.rainLevel,
            st.thunderLevel
        );
    }

    private static void setLevelsInstant(final RegionizedWorldData.RegionalWeatherState st, final float rain, final float thunder) {
        try {
            st.oRainLevel = rain;
            st.oThunderLevel = thunder;
        } catch (Throwable ignored) {

        }

        st.rainLevel = rain;
        st.thunderLevel = thunder;
    }


}
