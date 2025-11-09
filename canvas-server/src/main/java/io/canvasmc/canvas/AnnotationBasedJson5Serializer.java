package io.canvasmc.canvas;

import com.google.common.collect.Lists;
import io.canvasmc.canvas.configuration.ConfigSerializer;
import io.canvasmc.canvas.configuration.Configuration;
import io.canvasmc.canvas.configuration.jankson.Jankson;
import io.canvasmc.canvas.configuration.jankson.JsonArray;
import io.canvasmc.canvas.configuration.jankson.JsonElement;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.validator.AnnotationValidator;
import io.canvasmc.canvas.configuration.validator.ValidationResult;
import io.canvasmc.canvas.configuration.writer.Util;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Note: 'C' is 'Config', or the configuration class type
@SuppressWarnings("rawtypes")
public record AnnotationBasedJson5Serializer<C>(Configuration definition, Class<C> configClass,
                                                Jankson jankson,
                                                List<AnnotationValidator> validators,
                                                Consumer<Json5Builder.PostContext<C>> postInit,
                                                String header,
                                                Supplier<C> createInstance) implements ConfigSerializer<C> {
    public static final Logger LOGGER = LoggerFactory.getLogger("Json5Serializer");

    public AnnotationBasedJson5Serializer(Class<C> configClass, Consumer<Json5Builder.PostContext<C>> postInit, String header, @NotNull Function<Jankson.Builder, Jankson.Builder> hook, Supplier<C> createInstance) {
        this(
            Objects.requireNonNull(configClass.getAnnotation(Configuration.class), "Class must contain a Configuration annotation"),
            configClass, hook.apply(Jankson.builder()).build(), buildValidators(), postInit, header, createInstance
        );
    }

    private static @NotNull List<AnnotationValidator> buildValidators() {
        List<AnnotationValidator> services = Lists.newArrayList();
        ServiceLoader<AnnotationValidator> loader = ServiceLoader.load(AnnotationValidator.class);
        if (loader.stream().toList().isEmpty()) {
            LOGGER.warn("No services found for {}", AnnotationValidator.class.getName());
            return services;
        }

        for (final AnnotationValidator t : loader) {
            LOGGER.info("Loading class {} into registries for {}", t.getClass().getSimpleName(), AnnotationValidator.class.getName());
            services.add(t);
        }
        return services;
    }

    private @NotNull Path getConfigPath() {
        return getConfigFolder().resolve(this.definition.value() + ".json5");
    }

    @Override
    public void write(C config) throws SerializationException {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            // realistically we want to build a diff of the config options
            // and then only serialize the new ones added to the config
            // and remove ones that were removed from the config
            if (!configPath.toFile().exists()) {
                LOGGER.info("Config file doesn't exist, flooding with full config write");
                BufferedWriter writer = Files.newBufferedWriter(configPath);
                // only write header once, when we fill config.
                // this allows people to modify or remove the header later
                final String file = Util.wrapString(header) + "\n" +
                    jankson.toJson(config).toJson(true, true);
                writer.write(file);
                writer.close();
                this.read();
            } else {
                try {
                    String[] split = Util.splitHeader(Files.readString(configPath.toAbsolutePath()));
                    JsonObject disk = jankson.load(split[1]);
                    JsonObject memory = (JsonObject) jankson.toJson(config);
                    LOGGER.info("Config file exists, building diff");
                    // build a diff to see what 'disk' doesn't have in
                    // comparison to 'memory'
                    Util.Diff diff = Util.diff(disk, memory);
                    if (diff.added().isEmpty() && diff.removed().isEmpty()) {
                        // no diff, no need to write anything
                        LOGGER.info("No diff between disk and memory");
                        return;
                    }

                    // there is a diff, we need to write to disk
                    BufferedWriter writer = Files.newBufferedWriter(configPath);

                    // add the new added keys and their comments
                    for (final String key : diff.added()) {
                        LOGGER.info("Added key '{}' to config file", key);
                        // the key is being added from memory, meaning the comment
                        // is in memory, so we need to read from memory
                        String comment = Util.getCommentByPath(memory, key);
                        Util.putByPath(disk, key, Objects.requireNonNull(Util.getValueByPath(memory, key), "value cannot be null when placing new entry"), comment);
                    }

                    // find the comments from disk and remove them along with the key
                    for (final String key : diff.removed()) {
                        LOGGER.info("Removed key '{}' from config file", key);
                        Util.removeByPath(disk, key);
                    }

                    // write to disk
                    writer.write(
                        split[0] + "\n" + Util.cleanMultiLineCommentIndent(disk.toJson(true, true))
                    );
                    writer.close();
                } catch (Throwable e) {
                    throw new RuntimeException("Unable to build diff for config save", e);
                }
            }
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public @Nullable C read() throws SerializationException {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try {
                final JsonObject loaded = jankson.load(getConfigPath().toFile());
                C config = jankson.fromJson(loaded, configClass);
                // we are done reading, validate and run post
                if (config != null) {
                    LOGGER.info("Configuration loaded successfully, running validation and post consumer");
                    boolean[] failed = new boolean[]{false};
                    Util.forEach(configClass, loaded, (element, key, field) -> {
                        try {
                            for (final AnnotationValidator validator : this.validators) {
                                // field should be accessible already, no need to make it as such
                                Class<? extends Annotation> annotationClass = validator.typeOf();
                                if (field.isAnnotationPresent(annotationClass)) {
                                    Annotation annotation = field.getAnnotation(annotationClass);
                                    // special case for json arrays, as they can have validation per-entry since
                                    // they are a list of objects. normal json objects don't get special treatment
                                    List<JsonElement> toVerify = Lists.newLinkedList();
                                    if (element instanceof JsonArray array) {
                                        toVerify.addAll(array);
                                    } else toVerify.add(element);
                                    ValidationResult result = ValidationResult.PASS;
                                    for (final JsonElement jsonElement : toVerify) {
                                        //noinspection unchecked
                                        ValidationResult vr = validator.read(annotation, jsonElement);
                                        if (vr.equals(ValidationResult.FAIL)) {
                                            result = ValidationResult.FAIL;
                                            break;
                                        }
                                    }
                                    if (result == ValidationResult.FAIL) {
                                        LOGGER.error("Validation failed for entry '{}' in validator {}", key, validator.getClass().getName());
                                        failed[0] = true;
                                        break;
                                    }
                                }
                            }
                        } catch (Throwable thrown) {
                            LOGGER.error("Failed to run validation for {}", key, thrown);
                            failed[0] = true;
                        }
                    });
                    if (failed[0]) {
                        LOGGER.error("Couldn't fill configuration, exiting.");
                        return null;
                    } else {
                        this.postInit.accept(
                            new Json5Builder.PostContext<>(config, loaded.toJson(true, true))
                        );
                        LOGGER.info("Post consumer and validation completed successfully, saving changes to disk");
                    }
                }
                return config;
            } catch (Throwable e) {
                throw new SerializationException(e);
            }
        } else {
            LOGGER.info("No config file currently present, constructing default configuration");
            return createDefault();
        }
    }

    @Override
    public C createDefault() {
        return createInstance.get();
    }

    public static class Json5Builder<T> {
        private Class<T> classOf;
        private Consumer<PostContext<T>> postInit;
        private String header;
        private Function<Jankson.Builder, Jankson.Builder> func = (a) -> a;
        private Supplier<T> createDefault;

        public Json5Builder<T> classOf(Class<T> clazz) {
            this.classOf = clazz;
            return this;
        }

        public Json5Builder<T> post(Consumer<PostContext<T>> post) {
            this.postInit = post;
            return this;
        }

        public Json5Builder<T> hook(Function<Jankson.Builder, Jankson.Builder> func) {
            this.func = func;
            return this;
        }

        public Json5Builder<T> header(String header) {
            this.header = header;
            return this;
        }

        public Json5Builder<T> constructor(Supplier<T> createDefault) {
            this.createDefault = createDefault;
            return this;
        }

        public AnnotationBasedJson5Serializer<T> build() {
            return new AnnotationBasedJson5Serializer<>(
                classOf, postInit, header, func, createDefault
            );
        }

        public record PostContext<C>(C configuration, String contents) {
        }
    }
}
