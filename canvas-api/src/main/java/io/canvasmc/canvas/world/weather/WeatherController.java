package io.canvasmc.canvas.world.weather;

import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * API to read and change the weather of individual ticking regions,
 * when per-region weather is enabled.
 *
 * <p>Must be called from the tick thread owning the targeted region.</p>
 */
@NullMarked
public interface WeatherController {

    /**
     * Sets the weather of the region owning the given chunk.
     *
     * @param world         the world containing the region
     * @param chunkX        chunk X coordinate used to locate the region
     * @param chunkZ        chunk Z coordinate used to locate the region
     * @param type          target weather ({@code CLEAR}, {@code RAIN} or {@code THUNDER})
     * @param durationTicks how long to hold this weather, in ticks; a value {@code <= 0}
     *                      lets the current weather cycle continue naturally
     * @param instantLevels if {@code true}, applies the rain/thunder intensities
     *                      immediately instead of fading progressively
     * @throws IllegalStateException if not called from the region's tick thread
     */
    void setRegionalWeather(World world, int chunkX, int chunkZ, WeatherType type, int durationTicks, boolean instantLevels);

    /**
     * Sets the weather of the region owning the given location.
     *
     * @param loc           the location whose chunk determines the region
     * @param type          target weather ({@code CLEAR}, {@code RAIN} or {@code THUNDER})
     * @param durationTicks how long to hold this weather, in ticks; a value {@code <= 0}
     *                      lets the current weather cycle continue naturally
     * @param instantLevels if {@code true}, applies the rain/thunder intensities
     *                      immediately instead of fading progressively
     * @throws IllegalStateException if not called from the region's tick thread
     * @see #setRegionalWeather(World, int, int, WeatherType, int, boolean)
     */
    default void setRegionalWeather(Location loc, WeatherType type, int durationTicks, boolean instantLevels) {
        final int chunkX = loc.getBlockX() >> 4;
        final int chunkZ = loc.getBlockZ() >> 4;
        setRegionalWeather(loc.getWorld(), chunkX, chunkZ, type, durationTicks, instantLevels);
    }

    /**
     * Returns a snapshot of the weather of the region owning the given chunk.
     *
     * @param world  the world containing the region
     * @param chunkX chunk X coordinate used to locate the region
     * @param chunkZ chunk Z coordinate used to locate the region
     * @return the current weather state of the region, or {@code null} if
     * per-region weather is disabled for this world
     * @throws IllegalStateException if not called from the region's tick thread
     */
    @Nullable RegionalWeatherSnapshot getRegionalWeather(World world, int chunkX, int chunkZ);

    /**
     * Returns a snapshot of the weather of the region owning the given location.
     *
     * @param loc the location whose chunk determines the region
     * @return the current weather state of the region, or {@code null} if
     * per-region weather is disabled for this world
     * @throws IllegalStateException if not called from the region's tick thread
     * @see #getRegionalWeather(World, int, int)
     */
    default @Nullable RegionalWeatherSnapshot getRegionalWeather(Location loc) {
        final int chunkX = loc.getBlockX() >> 4;
        final int chunkZ = loc.getBlockZ() >> 4;
        return getRegionalWeather(loc.getWorld(), chunkX, chunkZ);
    }
}
