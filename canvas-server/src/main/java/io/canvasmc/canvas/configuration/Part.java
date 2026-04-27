package io.canvasmc.canvas.configuration;

import java.util.HashMap;
import java.util.Map;

public class Part {
    // we don't need or care about this being linked tbh
    final Map<String, String> styles = new HashMap<>();

    static Map<String, String> harvest(Class<? extends Part> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance().styles;
        } catch (Exception e) {
            throw new RuntimeException("Could not instantiate Part subclass " + clazz.getName()
                + " — ensure it has a public no-arg constructor", e);
        }
    }

    public void defineStyle(String memberName, String style) {
        styles.put(memberName, style);
    }
}
