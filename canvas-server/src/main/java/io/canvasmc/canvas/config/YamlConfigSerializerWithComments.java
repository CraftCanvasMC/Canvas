package io.canvasmc.canvas.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.DumperOptions;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.Yaml;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.constructor.Constructor;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.representer.Representer;
import org.jetbrains.annotations.NotNull;

public class YamlConfigSerializerWithComments<T extends ConfigData> implements ConfigSerializer<T> {
    private final Config definition;
    private final Class<T> configClass;
    private final Yaml yaml;

    public YamlConfigSerializerWithComments(Config definition, Class<T> configClass, Yaml yaml) {
        this.definition = definition;
        this.configClass = configClass;
        this.yaml = yaml;
    }

    public YamlConfigSerializerWithComments(Config definition, Class<T> configClass) {
        this(definition, configClass, new Yaml());
    }

    public static @NotNull String applyComments(@NotNull String yamlContent, @NotNull Map<String, String> comments) {
        String[] lines = yamlContent.split("\n", 2);
        String yamlBody = lines.length > 1 ? lines[1] : "";

        Yaml yaml = new Yaml(new Constructor());
        Map<String, Object> data = yaml.load(new StringReader(yamlBody));

        Map<String, Object> rebuiltData = sort(comments, data);

        StringWriter stringWriter = new StringWriter();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(new Constructor(), new Representer(), options);

        writeYaml(stringWriter, rebuiltData, comments, yaml, 0, "");

        return stringWriter.toString();
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<String, Object> sort(@NotNull Map<String, String> comments, Map<String, Object> data) {
        Map<String, Object> rebuiltData = new LinkedHashMap<>();

        for (final String fullKey : comments.keySet()) {
            String[] keys = fullKey.split("\\.");
            Map<String, Object> currentMap = rebuiltData;
            Map<?, ?> latest = data;

            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];

                if (i == keys.length - 1) {
                    Object value = latest.get(key);
                    currentMap.put(key, value);
                    break;
                }

                Object next = latest.get(key);
                if (next instanceof Map<?, ?> nextMap) {
                    latest = nextMap;
                }

                currentMap = (Map<String, Object>) currentMap.computeIfAbsent(key, _ -> new LinkedHashMap<>());
            }
        }
        return rebuiltData;
    }

    @SuppressWarnings("unchecked")
    private static void writeYaml(StringWriter writer, @NotNull Map<String, Object> data, Map<String, String> comments, Yaml yaml, int indentLevel, String parentKey) {
        String indent = "   ".repeat(indentLevel);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;

            String comment = comments.get(fullKey);
            if (comment != null) {
                writer.append(indent).append("## ").append(comment).append("\n");
            }

            if (value instanceof Map) {
                writer.append(indent).append(key).append(":\n");
                writeYaml(writer, (Map<String, Object>) value, comments, yaml, indentLevel + 1, fullKey);
            } else {
                writer.append(indent).append(key).append(": ").append(value.toString()).append("\n");
            }
        }
    }

    private @NotNull Path getConfigPath() {
        return Utils.getConfigFolder().resolve(this.definition.name() + ".yml");
    }

    public void serialize(T config) throws ConfigSerializer.SerializationException {
        Path configPath = this.getConfigPath();

        try {
            Files.createDirectories(configPath.getParent());
            String yamlContents = this.yaml.dump(config);
            ConfigurationUtils.extractKeys(configClass);
            String commentedContents = applyComments(yamlContents, ConfigurationUtils.COMMENTS);
            Files.writeString(configPath, commentedContents);
            ConfigurationUtils.hookToSpark(configPath);
        } catch (IOException e) {
            throw new ConfigSerializer.SerializationException(e);
        }
    }

    public T deserialize() throws ConfigSerializer.SerializationException {
        Path configPath = this.getConfigPath();
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                return this.yaml.load("!!io.canvasmc.canvas.Config\n" + content);
            } catch (IOException e) {
                throw new ConfigSerializer.SerializationException(e);
            }
        } else {
            return this.createDefault();
        }
    }

    public T createDefault() {
        return Utils.constructUnsafely(this.configClass);
    }
}
