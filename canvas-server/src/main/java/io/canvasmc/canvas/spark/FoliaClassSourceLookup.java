package io.canvasmc.canvas.spark;

import java.lang.reflect.Field;
import me.lucko.spark.paper.common.sampler.source.ClassSourceLookup;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaClassSourceLookup extends ClassSourceLookup.ByClassLoader {
    private static final Class<?> PLUGIN_CLASS_LOADER;
    private static final Field PLUGIN_FIELD;

    private static final Class<?> PAPER_PLUGIN_CLASS_LOADER;
    private static final Field PAPER_PLUGIN_FIELD;

    static {
        try {
            PLUGIN_CLASS_LOADER = Class.forName("org.bukkit.plugin.java.PluginClassLoader");
            PLUGIN_FIELD = PLUGIN_CLASS_LOADER.getDeclaredField("plugin");
            PLUGIN_FIELD.setAccessible(true);

            PAPER_PLUGIN_CLASS_LOADER = Class.forName("io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader");
            PAPER_PLUGIN_FIELD = PAPER_PLUGIN_CLASS_LOADER.getDeclaredField("loadedJavaPlugin");
            PAPER_PLUGIN_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public String identify(ClassLoader loader) throws ReflectiveOperationException {
        if (PLUGIN_CLASS_LOADER.isInstance(loader)) {
            JavaPlugin plugin = (JavaPlugin) PLUGIN_FIELD.get(loader);
            return plugin.getName();
        } else if (PAPER_PLUGIN_CLASS_LOADER.isInstance(loader)) {
            JavaPlugin plugin = (JavaPlugin) PAPER_PLUGIN_FIELD.get(loader);
            return plugin.getName();
        }
        return null;
    }

}
