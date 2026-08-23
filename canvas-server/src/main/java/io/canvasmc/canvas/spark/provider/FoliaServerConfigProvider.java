package io.canvasmc.canvas.spark.provider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.lucko.spark.paper.common.platform.serverconfig.ConfigParser;
import me.lucko.spark.paper.common.platform.serverconfig.ExcludedConfigFilter;
import me.lucko.spark.paper.common.platform.serverconfig.PropertiesConfigParser;
import me.lucko.spark.paper.common.platform.serverconfig.ServerConfigProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public class FoliaServerConfigProvider extends ServerConfigProvider {

    /**
     * A map of provided files and their type
     */
    private static final Map<String, ConfigParser> FILES;
    /**
     * A collection of paths to be excluded from the files
     */
    private static final Collection<String> HIDDEN_PATHS;

    static {
        final ImmutableMap.Builder<String, ConfigParser> files = ImmutableMap.<String, ConfigParser>builder()
            .put("server.properties", PropertiesConfigParser.INSTANCE)
            .put("bukkit.yml", YamlConfigParser.INSTANCE)
            .put("spigot.yml", YamlConfigParser.INSTANCE)
            .put("paper.yml", YamlConfigParser.INSTANCE)
            .put("canvas/", CanvasSplitParser.INSTANCE)
            .put("paper/", SplitYamlConfigParser.INSTANCE)
            .put("purpur.yml", YamlConfigParser.INSTANCE)
            .put("pufferfish.yml", YamlConfigParser.INSTANCE);

        for (final String config : getSystemPropertyList("spark.serverconfigs.extra")) {
            files.put(config, YamlConfigParser.INSTANCE);
        }

        final ImmutableSet.Builder<String> hiddenPaths = ImmutableSet.<String>builder()
            .add("database")
            .add("settings.bungeecord-addresses")
            .add("settings.velocity-support.secret")
            .add("proxies.velocity.secret")
            .add("server-ip")
            .add("motd")
            .add("resource-pack")
            .add("rcon<dot>password")
            .add("rcon<dot>ip")
            .add("level-seed")
            .add("world-settings.*.feature-seeds")
            .add("world-settings.*.seed-*")
            .add("feature-seeds")
            .add("seed-*")
            .addAll(getTimingsHiddenConfigs())
            .addAll(getSystemPropertyList("spark.serverconfigs.hiddenpaths"));

        FILES = files.build();
        HIDDEN_PATHS = hiddenPaths.build();
    }

    public FoliaServerConfigProvider() {
        super(FILES, HIDDEN_PATHS);
    }

    @Unmodifiable
    private static List<String> getTimingsHiddenConfigs() {
        return Collections.emptyList();
    }

    private static class YamlConfigParser implements ConfigParser {
        public static final YamlConfigParser INSTANCE = new YamlConfigParser();
        protected static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(MemorySection.class, (JsonSerializer<MemorySection>) (obj, _, ctx) -> ctx.serialize(obj.getValues(false)))
            .create();

        @Nullable
        @Override
        public JsonElement load(final String file, final ExcludedConfigFilter filter) throws IOException {
            final Map<String, Object> values = this.parse(Paths.get(file));
            if (values == null) {
                return null;
            }

            return filter.apply(GSON.toJsonTree(values));
        }

        @Override
        public Map<String, Object> parse(final BufferedReader reader) {
            final YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            return config.getValues(false);
        }
    }

    private static class CanvasSplitParser extends YamlConfigParser {
        public static final CanvasSplitParser INSTANCE = new CanvasSplitParser();

        @Nullable
        @Override
        public JsonElement load(final String group, final ExcludedConfigFilter filter) throws IOException {
            final Path configDir = Paths.get("config");
            if (!Files.exists(configDir)) {
                return null;
            }

            final JsonObject root = new JsonObject();

            for (final Map.Entry<String, Path> entry : getNestedFiles(configDir).entrySet()) {
                final String fileName = entry.getKey();
                final Path path = entry.getValue();

                final Map<String, Object> values = this.parse(path);
                if (values == null) {
                    continue;
                }

                // apply the filter individually to each nested file
                root.add(fileName, filter.apply(GSON.toJsonTree(values)));
            }

            return root;
        }

        private static Map<String, Path> getNestedFiles(final Path configDir) {
            final Map<String, Path> files = new LinkedHashMap<>();
            files.put("canvas-server.yml", configDir.resolve("canvas-server.yml"));
            files.put("world-defaults.yml", configDir.resolve("canvas-worlds.yml"));
            for (final World world : Bukkit.getWorlds()) {
                files.put(world.getName() + ".yml", world.getWorldFolder().toPath().resolve("canvas-patch.yml"));
            }
            return files;
        }
    }

    // Paper 1.19+ split config layout
    private static class SplitYamlConfigParser extends YamlConfigParser {
        public static final SplitYamlConfigParser INSTANCE = new SplitYamlConfigParser();

        @Nullable
        @Override
        public JsonElement load(final String group, final ExcludedConfigFilter filter) throws IOException {
            final String prefix = group.replace("/", "");
            final Path configDir = Paths.get("config");

            if (!Files.exists(configDir)) {
                return null;
            }

            final JsonObject root = new JsonObject();

            for (final Map.Entry<String, Path> entry : getNestedFiles(configDir, prefix).entrySet()) {
                final String fileName = entry.getKey();
                final Path path = entry.getValue();

                final Map<String, Object> values = this.parse(path);
                if (values == null) {
                    continue;
                }

                // apply the filter individually to each nested file
                root.add(fileName, filter.apply(GSON.toJsonTree(values)));
            }

            return root;
        }

        private static Map<String, Path> getNestedFiles(final Path configDir, final String prefix) {
            final Map<String, Path> files = new LinkedHashMap<>();
            files.put("global.yml", configDir.resolve(prefix + "-global.yml"));
            files.put("world-defaults.yml", configDir.resolve(prefix + "-world-defaults.yml"));
            for (final World world : Bukkit.getWorlds()) {
                files.put(world.getName() + ".yml", world.getWorldFolder().toPath().resolve(prefix + "-world.yml"));
            }
            return files;
        }
    }

}
