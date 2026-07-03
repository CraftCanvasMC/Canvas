package io.canvasmc.canvas.spark.provider;

import java.lang.reflect.Field;
import me.lucko.spark.paper.common.sampler.source.ClassSourceLookup;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

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
        } catch (final ReflectiveOperationException roe) {
            throw new ExceptionInInitializerError(roe);
        }
    }

    @Nullable
    @Override
    public String identify(final ClassLoader loader) throws ReflectiveOperationException {
        if (PLUGIN_CLASS_LOADER.isInstance(loader)) {
            final JavaPlugin plugin = (JavaPlugin) PLUGIN_FIELD.get(loader);
            return plugin.getName();
        }
        else if (PAPER_PLUGIN_CLASS_LOADER.isInstance(loader)) {
            final JavaPlugin plugin = (JavaPlugin) PAPER_PLUGIN_FIELD.get(loader);
            return plugin.getName();
        }
        return null;
    }

}
