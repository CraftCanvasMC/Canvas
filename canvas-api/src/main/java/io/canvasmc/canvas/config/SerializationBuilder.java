package io.canvasmc.canvas.config;

import io.canvasmc.canvas.config.annotation.RegisteredHandler;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("rawtypes")
public class SerializationBuilder<C> {
    private final List<WrappedContextProvider> contextProviders = new ArrayList<>();
    private final List<WrappedValidationProvider> validationProviders = new ArrayList<>();
    private final List<Consumer<AnnotationBasedYamlSerializer.PostSerializeContext<C>>> postConsumers = new ArrayList<>();
    private String[] header = new String[0];
    private boolean immutable = false;

    SerializationBuilder() {
    }

    @Contract(value = " -> new", pure = true)
    public static <C> @NotNull SerializationBuilder<C> newBuilder() {
        return new SerializationBuilder<>();
    }

    public <T extends Annotation> SerializationBuilder<C> handler(@NotNull Supplier<AnnotationContextProvider<T>> contextProviderSupplier) {
        return handler(contextProviderSupplier.get());
    }

    public <T extends Annotation> SerializationBuilder<C> handler(AnnotationContextProvider<T> contextProvider) {
        if (immutable) throw new RuntimeException("Unable to mutate builder, already built!");
        contextProviders.add(new WrappedContextProvider<>(contextProvider, fetchOrThrow(contextProvider)));
        return this;
    }

    public <T extends Annotation> SerializationBuilder<C> validator(@NotNull Supplier<AnnotationValidationProvider<T>> validationProviderSupplier) {
        return validator(validationProviderSupplier.get());
    }

    public <T extends Annotation> SerializationBuilder<C> validator(AnnotationValidationProvider<T> validationProvider) {
        if (immutable) throw new RuntimeException("Unable to mutate builder, already built!");
        validationProviders.add(new WrappedValidationProvider<>(validationProvider, fetchOrThrow(validationProvider)));
        return this;
    }

    private Class fetchOrThrow(@NotNull Object provider) {
        try {
            return (Class) provider.getClass().getDeclaredMethod(provider.getClass().getDeclaredAnnotation(RegisteredHandler.class).value()).invoke(provider);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Unable to fetch invoker", e);
        }
    }

    public SerializationBuilder<C> header(String[] header) {
        if (immutable) throw new RuntimeException("Unable to mutate builder, already built!");
        this.header = header;
        return this;
    }

    public SerializationBuilder<C> post(Consumer<AnnotationBasedYamlSerializer.PostSerializeContext<C>> contextConsumer) {
        if (immutable) throw new RuntimeException("Unable to mutate builder, already built!");
        postConsumers.add(contextConsumer);
        return this;
    }

    public SerializationBuilder.Final<C> build(Configuration definition, Class<C> owningClass) {
        this.immutable = true;
        return new Final<>(definition, owningClass, this.contextProviders, this.validationProviders, this.postConsumers, this.header);
    }

    public record WrappedContextProvider<A extends Annotation>(AnnotationContextProvider<A> provider,
                                                               Class<A> annotation) {
    }

    public record WrappedValidationProvider<A extends Annotation>(AnnotationValidationProvider<A> provider,
                                                                  Class<A> annotation) {
    }

    public static final class Final<C> {
        private final Configuration definition;
        private final Class<C> owningClass;
        private final List<WrappedContextProvider> contextProviders;
        private final List<WrappedValidationProvider> validationProviders;
        private final List<Consumer<AnnotationBasedYamlSerializer.PostSerializeContext<C>>> postConsumers;
        private final String[] header;

        public Final(Configuration definition, Class<C> owningClass,
                     List<WrappedContextProvider> contextProviders,
                     List<WrappedValidationProvider> validationProviders,
                     List<Consumer<AnnotationBasedYamlSerializer.PostSerializeContext<C>>> postConsumers,
                     String[] header) {
            this.definition = definition;
            this.owningClass = owningClass;
            this.contextProviders = contextProviders;
            this.validationProviders = validationProviders;
            this.postConsumers = postConsumers;
            this.header = header;
        }

        public Configuration definition() {
            return definition;
        }

        public Class<C> owningClass() {
            return owningClass;
        }

        public List<Consumer<AnnotationBasedYamlSerializer.PostSerializeContext<C>>> postConsumers() {
            return postConsumers;
        }

        public String[] header() {
            return this.header;
        }

        public @NotNull Map<Class<? extends Annotation>, AnnotationContextProvider> wrappedContextMap() {
            Map<Class<? extends Annotation>, AnnotationContextProvider> retVal = new HashMap<>();
            for (final WrappedContextProvider contextProvider : this.contextProviders) {
                //noinspection unchecked
                retVal.put(contextProvider.annotation, contextProvider.provider);
            }
            return retVal;
        }

        public @NotNull Map<Class<? extends Annotation>, AnnotationValidationProvider> wrappedValidationMap() {
            Map<Class<? extends Annotation>, AnnotationValidationProvider> retVal = new HashMap<>();
            for (final WrappedValidationProvider contextProvider : this.validationProviders) {
                //noinspection unchecked
                retVal.put(contextProvider.annotation, contextProvider.provider);
            }
            return retVal;
        }
    }
}
