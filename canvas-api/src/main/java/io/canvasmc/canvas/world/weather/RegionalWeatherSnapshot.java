package io.canvasmc.canvas.world.weather;

public record RegionalWeatherSnapshot(
    long regionId,
    boolean raining,
    boolean thundering,
    int clearTime,
    int rainTime,
    int thunderTime,
    float rainLevel,
    float thunderLevel
) {}
