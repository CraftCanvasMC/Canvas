package io.canvasmc.canvas.world.weather;

/**
 * Target weather states usable with
 * {@link WeatherController#setRegionalWeather(org.bukkit.World, int, int, WeatherType, int, boolean)}.
 */
public enum WeatherType {

    /**
     * No precipitation: rain and thunder stop, intensity levels fade to {@code 0}.
     */
    CLEAR,

    /**
     * Rain without thunder.
     */
    RAIN,

    /**
     * Thunderstorm. Implies rain: both rain and thunder levels are raised.
     */
    THUNDER
}
