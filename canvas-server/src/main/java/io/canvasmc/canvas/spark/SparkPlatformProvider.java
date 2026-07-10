package io.canvasmc.canvas.spark;

import java.lang.reflect.Field;
import java.util.Optional;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.paper.common.SparkPlatform;
import me.lucko.spark.paper.common.api.SparkApi;

public class SparkPlatformProvider {
    private static final Optional<Field> SPARK_API_PLATFORM_FIELD;

    static {
        Optional<Field> platformField;
        try {
            final Class<?> classSparkApi = Class.forName("me.lucko.spark.common.api.SparkApi");
            final Field fieldPlatform = classSparkApi.getDeclaredField("platform");

            fieldPlatform.setAccessible(true);

            platformField = Optional.of(fieldPlatform);
        } catch (final Throwable ignored) {
            platformField = Optional.empty();
        }
        SPARK_API_PLATFORM_FIELD = platformField;
    }

    /**
     * Gets the {@link me.lucko.spark.paper.common.SparkPlatform} instance for the current runtime
     *
     * @return The spark platform instance
     */
    public static SparkPlatform getSparkPlatform() {
        final Spark spark = SparkProvider.get();
        if (spark instanceof SparkApi sparkApi) {
            return SPARK_API_PLATFORM_FIELD.map((field) -> {
                try {
                    return (SparkPlatform) field.get(sparkApi);
                } catch (final IllegalAccessException iae) {
                    throw new RuntimeException("Couldn't get spark platform", iae);
                }
            }).orElseThrow(() -> new IllegalStateException("Platform field for accessor was not filled?"));
        }
        else {
            throw new IllegalStateException("Implementation \"" + spark.getClass().getName() + "\" not supported");
        }
    }
}
