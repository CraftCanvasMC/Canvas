package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.validation.NumberComparison;
import io.canvasmc.canvas.configuration.validation.StringValidation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class Part {

    // we don't need or care about this being linked tbh
    final Object2ObjectOpenHashMap<String, OptionDefinition> definitions = new Object2ObjectOpenHashMap<>();
    {
        // set the default return value to a blank definition
        definitions.defaultReturnValue(new OptionDefinition());
    }

    static Map<String, OptionDefinition> harvest(Class<? extends Part> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance().definitions;
        } catch (Exception e) {
            throw new RuntimeException("Could not instantiate Part subclass " + clazz.getName()
                + " — ensure it has a public no-arg constructor", e);
        }
    }

    public OptionDefinition option(String target) {

        // target must exist or we throw

        try {
            getClass().getField(target);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Target '" + target + "' doesn't exist");
        }

        final OptionDefinition def = new OptionDefinition();

        // make sure we propagate in definitions
        definitions.put(target, def);

        return def;
    }

    public interface Validation<T> {
        void validate(T t);
    }

    public static class OptionDefinition {
        protected final List<Validation<?>> validations = new LinkedList<>();
        private Style style;

        public @Nullable Style commentStyle() {
            return this.style;
        }

        public OptionDefinition docs(Style style) {
            this.style = style;
            return this;
        }

        public OptionDefinition docs(String... toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition docs(String toBeWrapped) {
            return docs(Style.wrap(toBeWrapped));
        }

        public OptionDefinition greaterThan(float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN, val));
        }

        public OptionDefinition greaterThanOrEqualTo(float val) {
            return validation(new NumberComparison(NumberComparison.Type.GREATER_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition lessThan(float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN, val));
        }

        public OptionDefinition lessThanOrEqualTo(float val) {
            return validation(new NumberComparison(NumberComparison.Type.LESS_THAN_OR_EQUAL_TO, val));
        }

        public OptionDefinition equals(float val) {
            return validation(new NumberComparison(NumberComparison.Type.EQUAL, val));
        }

        public OptionDefinition between(float min, float max) {
            return validation(new NumberComparison(NumberComparison.Type.BETWEEN, min, max));
        }

        public OptionDefinition greedyString() {
            return validation(new StringValidation(StringValidation.StringType.GREEDY_PHRASE));
        }

        public OptionDefinition identifier() {
            return validation(new StringValidation(StringValidation.StringType.IDENTIFIER));
        }

        public OptionDefinition word() {
            return validation(new StringValidation(StringValidation.StringType.SINGLE_WORD));
        }

        public OptionDefinition validation(Validation<?> validation) {
            validations.add(validation);
            return this;
        }
    }
}
