package io.canvasmc.canvas.world.weather;

import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

public interface WeatherController {

    void setRegionalWeather(World world, int chunkX, int chunkZ, WeatherType type, int durationTicks, boolean instantLevels);

    default void setRegionalWeather(Location loc, WeatherType type, int durationTicks, boolean instantLevels) {
        final int cx = loc.getBlockX() >> 4;
        final int cz = loc.getBlockZ() >> 4;
        setRegionalWeather(loc.getWorld(), cx, cz, type, durationTicks, instantLevels);
    }

    @Nullable RegionalWeatherSnapshot getRegionalWeather(World world, int chunkX, int chunkZ);

    default @Nullable RegionalWeatherSnapshot getRegionalWeather(Location loc) {
        final int cx = loc.getBlockX() >> 4;
        final int cz = loc.getBlockZ() >> 4;
        return getRegionalWeather(loc.getWorld(), cx, cz);
    }
}
