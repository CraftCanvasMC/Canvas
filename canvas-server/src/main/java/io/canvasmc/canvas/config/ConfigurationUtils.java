package io.canvasmc.canvas.config;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ConfigurationUtils {
    protected static final Map<String, Comment> COMMENTS = new LinkedHashMap<>();

    public static void extractKeys(Class<?> clazz) {
        extractKeys(clazz, "");
    }

    static @NotNull List<String> extractKeys(@NotNull Class<?> clazz, String prefix) {
        List<String> keys = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                String keyName = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                keys.add(keyName);

                if (field.isAnnotationPresent(Comment.class)) {
                    Comment comment = field.getAnnotation(Comment.class);
                    COMMENTS.put(keyName, comment);
                }

                if (field.getType().getEnclosingClass() == clazz) {
                    keys.addAll(extractKeys(field.getType(), keyName));
                }
            }
        }
        return keys;
    }

    public static void hookToSpark(@NotNull Path configPath) {
        // "spark.serverconfigs.extra"
        String name = configPath.toFile().getName();
        System.setProperty("spark.serverconfigs.extra", name);
    }
}
