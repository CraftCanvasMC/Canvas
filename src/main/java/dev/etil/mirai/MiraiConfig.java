package dev.etil.mirai;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.Nullable;
import org.simpleyaml.configuration.comments.CommentType;
import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.exceptions.InvalidConfigurationException;

public class MiraiConfig {

    private static final YamlFile config = new YamlFile();
    private static int updates = 0;

    private static ConfigurationSection convertToBukkit(org.simpleyaml.configuration.ConfigurationSection section) {
        ConfigurationSection newSection = new MemoryConfiguration();
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                newSection.set(key, convertToBukkit(section.getConfigurationSection(key)));
            } else {
                newSection.set(key, section.get(key));
            }
        }
        return newSection;
    }

    public static ConfigurationSection getConfigCopy() {
        return convertToBukkit(config);
    }

    public static int getUpdates() {
        return updates;
    }

    public static void load() throws IOException {
        File configFile = new File("mirai.yml");

        if (configFile.exists()) {
            try {
                config.load(configFile);
            } catch (InvalidConfigurationException e) {
                throw new IOException(e);
            }
        }

        getString("info.version", "2.1");
        setComment("info",
                "Mirai Configuration",
                "Join our Discord at https://discord.gg/DdH6Yfu9gM",
                "Download new builds at https://ci.codemc.io/job/etil2jz/job/Mirai-1.19/lastSuccessfulBuild/");

        for (Method method : MiraiConfig.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPrivate(method.getModifiers()) && method.getParameterCount() == 0 &&
                    method.getReturnType() == Void.TYPE && !method.getName().startsWith("lambda")) {
                method.setAccessible(true);
                try {
                    method.invoke(null);
                } catch (Throwable t) {
                    MinecraftServer.LOGGER.warn("Failed to load configuration option from " + method.getName(), t);
                }
            }
        }

        updates++;

        config.save(configFile);
    }

    private static void setComment(String key, String... comment) {
        if (config.contains(key)) {
            config.setComment(key, String.join("\n", comment), CommentType.BLOCK);
        }
    }

    private static void ensureDefault(String key, Object defaultValue, String... comment) {
        if (!config.contains(key)) {
            config.set(key, defaultValue);
            config.setComment(key, String.join("\n", comment), CommentType.BLOCK);
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue, String... comment) {
        return getBoolean(key, null, defaultValue, comment);
    }

    private static boolean getBoolean(String key, @Nullable String oldKey, boolean defaultValue, String... comment) {
        ensureDefault(key, defaultValue, comment);
        return config.getBoolean(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue, String... comment) {
        return getInt(key, null, defaultValue, comment);
    }

    private static int getInt(String key, @Nullable String oldKey, int defaultValue, String... comment) {
        ensureDefault(key, defaultValue, comment);
        return config.getInt(key, defaultValue);
    }

    private static double getDouble(String key, double defaultValue, String... comment) {
        return getDouble(key, null, defaultValue, comment);
    }

    private static double getDouble(String key, @Nullable String oldKey, double defaultValue, String... comment) {
        ensureDefault(key, defaultValue, comment);
        return config.getDouble(key, defaultValue);
    }

    private static String getString(String key, String defaultValue, String... comment) {
        return getOldString(key, null, defaultValue, comment);
    }

    private static String getOldString(String key, @Nullable String oldKey, String defaultValue, String... comment) {
        ensureDefault(key, defaultValue, comment);
        return config.getString(key, defaultValue);
    }

    private static List<String> getStringList(String key, List<String> defaultValue, String... comment) {
        return getStringList(key, null, defaultValue, comment);
    }

    private static List<String> getStringList(String key, @Nullable String oldKey, List<String> defaultValue, String... comment) {
        ensureDefault(key, defaultValue, comment);
        return config.getStringList(key);
    }

}
