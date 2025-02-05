package io.canvasmc.canvas.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.DumperOptions;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.Yaml;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.constructor.Constructor;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AnnotationBasedYamlSerializer<T extends ConfigData> implements ConfigSerializer<T> {
    private final Map<Class<? extends Annotation>, AnnotationContextProvider> annotationContextProviderRegistry = new HashMap<>();
    private final List<Consumer<PostSerializeContext<T>>> postConsumerContexts = new ArrayList<>();
    private final Config definition;
    private final Class<T> configClass;
    private final Yaml yaml;

    public AnnotationBasedYamlSerializer(Config definition, @NotNull Class<T> configClass, Yaml yaml) {
        this.definition = definition;
        this.configClass = configClass;
        this.yaml = yaml;
    }

    public AnnotationBasedYamlSerializer(Config definition, Class<T> configClass) {
        this(definition, configClass, new Yaml());
    }

    /**
     * Registers an annotation handler, which acts as a pre-processor before adding the key and its value to the yaml during serialization to disk
     *
     * @param annotation      the class of the annotation you are registering
     * @param contextProvider the context provider of the annotation you are registering
     */
    public <A extends Annotation> void registerAnnotationHandler(@NotNull Class<A> annotation, AnnotationContextProvider<A> contextProvider) {
        if (!annotation.isAnnotation()) {
            throw new IllegalArgumentException("Class must be an annotation");
        }
        annotationContextProviderRegistry.put(annotation, contextProvider);
    }

    /**
     * Post-serialize action. This triggers immediately after the config is written to disk, and acts as a post-processor for the config
     *
     * @param contextConsumer the consumer for the {@link PostSerializeContext}
     */
    public void registerPostSerializeAction(Consumer<PostSerializeContext<T>> contextConsumer) {
        this.postConsumerContexts.add(contextConsumer);
    }

    private @NotNull Map<String, Object> sortByKeys(@NotNull Set<String> keysOrder, Map<String, Object> data) {
        Map<String, Object> rebuiltData = new LinkedHashMap<>();

        for (final String fullKey : keysOrder) {
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

    private void writeYaml(StringWriter writer, @NotNull Map<String, Object> data, Map<String, Field> fields, int indentLevel, String parentKey) {
        String indent = "   ".repeat(indentLevel);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;

            if (value == null) {
                continue;
            }

            this.annotationContextProviderRegistry.forEach((annotationClass, contextProvider) -> {
                Field keyField = fields.get(fullKey);
                if (keyField == null) {
                    return;
                }
                if (keyField.isAnnotationPresent(annotationClass)) {
                    contextProvider.apply(writer, indent, fullKey, keyField, keyField.getAnnotation(annotationClass));
                }
            });

            if (value instanceof Map) {
                writer.append(indent).append(key).append(":\n");
                writeYaml(writer, (Map<String, Object>) value, fields, indentLevel + 1, fullKey);
            } else if (value instanceof List<?> list) {
                writer.append(indent).append(key).append(":");
                if (!list.isEmpty()) {
                    writer.append("\n");
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> itemMap) {
                            writer.append(indent).append("   -\n");
                            writeYaml(writer, (Map<String, Object>) itemMap, fields, indentLevel + 2, fullKey);
                        } else {
                            writer.append(indent).append("   - ").append(item.toString()).append("\n");
                        }
                    }
                } else {
                    writer.append(" []\n");
                }
            } else {
                writer.append(indent).append(key).append(": ").append(value.toString()).append("\n");
            }
        }
    }

    private @NotNull Path getConfigPath() {
        return Utils.getConfigFolder().resolve(this.definition.name() + ".yml");
    }

    @Override
    public void serialize(T config) throws ConfigSerializer.SerializationException {
        Path configPath = this.getConfigPath();

        try {
            Files.createDirectories(configPath.getParent());
            String yamlContent = this.yaml.dump(config);
            Map<String, Field> keyToField = ConfigurationUtils.FIELD_MAP;
            String[] lines = yamlContent.split("\n", 2);
            String body = lines.length > 1 ? lines[1] : "";

            Yaml yaml = new Yaml(new Constructor());
            Map<String, Object> data = yaml.load(new StringReader(body));

            Map<String, Object> reorderedData = sortByKeys(keyToField.keySet(), data);

            StringWriter yamlWriter = new StringWriter();
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

            writeYaml(yamlWriter, reorderedData, keyToField, 0, "");

            Files.writeString(configPath, yamlWriter.toString());
            PostSerializeContext context = new PostSerializeContext<>(configPath, config, createDefault(), yamlWriter.toString());
            postConsumerContexts.forEach(consumer -> consumer.accept(context));
        } catch (IOException e) {
            throw new ConfigSerializer.SerializationException(e);
        }
    }

    @Override
    public T deserialize() throws ConfigSerializer.SerializationException {
        Path configPath = this.getConfigPath();
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                return this.yaml.load("!!" + getConfigClass().getName() + "\n" + content);
            } catch (IOException e) {
                throw new ConfigSerializer.SerializationException(e);
            }
        } else {
            return this.createDefault();
        }
    }

    @Override
    public T createDefault() {
        return Utils.constructUnsafely(this.configClass);
    }

    public Class<?> getConfigClass() {
        return configClass;
    }

    public record PostSerializeContext<A extends ConfigData>(Path configPath, A configuration, A defaultConfiguration, String contents) {

    }
}
