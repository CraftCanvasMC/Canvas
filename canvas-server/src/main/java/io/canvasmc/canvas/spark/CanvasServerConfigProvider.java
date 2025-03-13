package io.canvasmc.canvas.spark;

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
import java.util.LinkedHashMap;
import java.util.Map;
import me.lucko.spark.paper.common.platform.serverconfig.ConfigParser;
import me.lucko.spark.paper.common.platform.serverconfig.ExcludedConfigFilter;
import me.lucko.spark.paper.common.platform.serverconfig.PropertiesConfigParser;
import me.lucko.spark.paper.common.platform.serverconfig.ServerConfigProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CanvasServerConfigProvider extends ServerConfigProvider {
    private static final Map<String, ConfigParser> FILES;
    private static final Collection<String> HIDDEN_PATHS;

    static {
        ImmutableMap.Builder<String, ConfigParser> filesBuilder = ImmutableMap.builder();
        filesBuilder.put("server.properties", PropertiesConfigParser.INSTANCE)
            .put("bukkit.yml", YamlConfigParser.INSTANCE)
            .put("spigot.yml", YamlConfigParser.INSTANCE)
            .put("paper.yml", YamlConfigParser.INSTANCE)
            .put("paper/", SplitYamlConfigParser.INSTANCE)
            .put("purpur.yml", YamlConfigParser.INSTANCE)
            .put("pufferfish.yml", YamlConfigParser.INSTANCE)
            .put("canvas_server.yml", YamlConfigParser.INSTANCE);

        for (String config : getSystemPropertyList("spark.serverconfigs.extra")) {
            filesBuilder.put(config, YamlConfigParser.INSTANCE);
        }

        ImmutableSet.Builder<String> hiddenPaths = ImmutableSet.builder();
        hiddenPaths.add("database")
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
            .addAll(getSystemPropertyList("spark.serverconfigs.hiddenpaths"));
        FILES = filesBuilder.build();
        HIDDEN_PATHS = hiddenPaths.build();
    }

    public CanvasServerConfigProvider() {
        super(FILES, HIDDEN_PATHS);
    }

    private static class YamlConfigParser implements ConfigParser {
        public static final YamlConfigParser INSTANCE = new YamlConfigParser();
        protected static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(MemorySection.class, (JsonSerializer<MemorySection>) (obj, _, ctx) -> ctx.serialize(obj.getValues(false)))
            .create();

        private YamlConfigParser() {
        }

        public JsonElement load(String file, ExcludedConfigFilter filter) throws IOException {
            Map<String, Object> values = this.parse(Paths.get(file));
            return values == null ? null : filter.apply(GSON.toJsonTree(values));
        }

        public Map<String, Object> parse(BufferedReader reader) throws IOException {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            return config.getValues(false);
        }
    }

    private static class SplitYamlConfigParser extends YamlConfigParser {
        public static final SplitYamlConfigParser INSTANCE = new SplitYamlConfigParser();

        private SplitYamlConfigParser() {
        }

        private static @NotNull Map<String, Path> getNestedFiles(@NotNull Path configDir, String prefix) {
            Map<String, Path> files = new LinkedHashMap<>();
            files.put("global.yml", configDir.resolve(prefix + "-global.yml"));
            files.put("world-defaults.yml", configDir.resolve(prefix + "-world-defaults.yml"));

            for (World world : Bukkit.getWorlds()) {
                files.put(world.getName() + ".yml", world.getWorldFolder().toPath().resolve(prefix + "-world.yml"));
            }

            return files;
        }

        public @Nullable JsonElement load(@NotNull String group, ExcludedConfigFilter filter) throws IOException {
            String prefix = group.replace("/", "");
            Path configDir = Paths.get("config");
            if (!Files.exists(configDir)) {
                return null;
            } else {
                JsonObject root = new JsonObject();

                for (Map.Entry<String, Path> entry : getNestedFiles(configDir, prefix).entrySet()) {
                    String fileName = entry.getKey();
                    Path path = entry.getValue();
                    Map<String, Object> values = this.parse(path);
                    if (values != null) {
                        root.add(fileName, filter.apply(GSON.toJsonTree(values)));
                    }
                }

                return root;
            }
        }
    }
}
