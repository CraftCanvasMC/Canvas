package io.canvasmc.canvas.config.impl;

import java.util.ServiceLoader;

public interface ConfigAccess {
    static ConfigAccess get() {
        return Holder.INSTANCE;
    }

    boolean containsField(String field);

    <T> T getField(String field);

    final class Holder {
        private static final ConfigAccess INSTANCE;

        static {
            INSTANCE = ServiceLoader.load(ConfigAccess.class, ClassLoader.getSystemClassLoader()).findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to locate ConfigAccess"));
        }

        private Holder() {
        }
    }
}
