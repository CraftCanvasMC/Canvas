package io.canvasmc.canvas.world.weather;

/**
 * Immutable snapshot of a region's weather at the time of the call.
 *
 * <p>Returned by {@link WeatherController#getRegionalWeather(org.bukkit.World, int, int)}.
 * It is a plain copy and does not reflect changes made after it was taken.</p>
 *
 * @param regionId     unique id of the ticking region
 * @param raining      whether the region is currently raining
 * @param thundering   whether the region is currently thundering
 * @param clearTime    remaining ticks of clear weather before the cycle may change
 * @param rainTime     remaining ticks of rain
 * @param thunderTime  remaining ticks of thunder
 * @param rainLevel    current rain intensity, in {@code [0..1]}; fades progressively
 *                     toward its target unless set instantly
 * @param thunderLevel current thunder intensity, in {@code [0..1]}
 */
public record RegionalWeatherSnapshot(
    long regionId,
    boolean raining,
    boolean thundering,
    int clearTime,
    int rainTime,
    int thunderTime,
    float rainLevel,
    float thunderLevel
) {
}
