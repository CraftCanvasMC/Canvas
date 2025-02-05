package io.canvasmc.canvas.util;

import org.jetbrains.annotations.Nullable;

public class StringUtils {
    public static boolean hasLength(@Nullable String str) {
        return (str != null && !str.isEmpty());
    }
}
