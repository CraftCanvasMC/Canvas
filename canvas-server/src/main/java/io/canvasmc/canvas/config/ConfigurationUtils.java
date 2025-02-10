package io.canvasmc.canvas.config;

import io.canvasmc.canvas.config.annotation.Comment;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ConfigurationUtils {
    protected static final Map<String, Comment> COMMENTS = new LinkedHashMap<>();
    protected static final Map<String, Field> FIELD_MAP = new LinkedHashMap<>();

    public static void extractKeys(Class<?> clazz) {
        extractKeys(clazz, "");
    }

    static @NotNull List<String> extractKeys(@NotNull Class<?> clazz, String prefix) {
        List<String> keys = new LinkedList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                String keyName = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
                keys.add(keyName);

                if (field.isAnnotationPresent(Comment.class)) {
                    Comment comment = field.getAnnotation(Comment.class);
                    COMMENTS.put(keyName, comment);
                }

                FIELD_MAP.put(keyName, field);
                if (field.getType().getEnclosingClass() == clazz) {
                    keys.addAll(extractKeys(field.getType(), keyName));
                }
            }
        }
        return keys;
    }

}
