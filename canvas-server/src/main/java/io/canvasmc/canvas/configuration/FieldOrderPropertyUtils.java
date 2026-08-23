package io.canvasmc.canvas.configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.FieldProperty;
import org.yaml.snakeyaml.introspector.GenericProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class FieldOrderPropertyUtils extends PropertyUtils {

    public FieldOrderPropertyUtils() {
        super.setBeanAccess(BeanAccess.FIELD);
    }

    public static String toKebabCase(final String camel) {
        Objects.requireNonNull(camel);

        // if camel-case version is empty, nothing to convert
        if (camel.isEmpty()) return camel;

        final StringBuilder sb = new StringBuilder(camel.length() + 4);
        final char[] chars = camel.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (Character.isUpperCase(c)) {
                final boolean prevIsLower = i > 0 && !Character.isUpperCase(chars[i - 1]);
                final boolean nextIsLower = i + 1 < chars.length && Character.isLowerCase(chars[i + 1]);
                if (i > 0 && (prevIsLower || nextIsLower)) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static String toCamelCase(final String kebab) {
        Objects.requireNonNull(kebab);

        // if this doesn't contain a dash, the output is always the same
        if (!kebab.contains("-")) return kebab;

        final StringBuilder sb = new StringBuilder(kebab.length());
        boolean nextUpper = false;

        for (final char c : kebab.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            }
            else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            }
            else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    @Override
    protected Set<Property> createPropertySet(final Class<?> type, final BeanAccess bAccess) {
        // get the default property set from snakeyaml
        final Collection<Property> properties = super.createPropertySet(type, bAccess);

        // build a declaration-order index from reflection
        // getDeclaredFields returns fields in declaration order per the JVM spec
        // we walk the hierarchy too so nested/inherited fields sort correctly

        // filter out any fields declared in Part itself
        final Set<String> partFields = Arrays.stream(Part.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        final List<String> declarationOrder = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Arrays.stream(current.getDeclaredFields())
                .filter(field -> !field.accessFlags().contains(AccessFlag.STATIC))
                .map(Field::getName)
                .filter(name -> !partFields.contains(name))
                .forEach(declarationOrder::add);
            current = current.getSuperclass();
        }

        return properties.stream()
            .filter(abstractProperty -> {
                // the initial filter should filter anything final
                if (abstractProperty instanceof FieldProperty fieldProperty) {
                    try {
                        // get the field from the "field" field in the field property
                        final Field storedFieldField = FieldProperty.class.getDeclaredField("field");
                        storedFieldField.setAccessible(true);
                        final Field storedField = (Field) storedFieldField.get(fieldProperty);

                        // fields with final modifiers should be ignored from the configurations
                        if (storedField.accessFlags().contains(AccessFlag.FINAL)) {
                            return false;
                        }
                    } catch (final NoSuchFieldException nsfe) {
                        throw new RuntimeException("Field was not found", nsfe);
                    } catch (final IllegalAccessException iae) {
                        throw new RuntimeException("Unable to access field", iae);
                    }
                }
                // the field shouldn't be final, or not field property! yippeee :)
                return true;
            })
            .filter(abstractProperty -> !partFields.contains(
                abstractProperty instanceof KebabCaseProperty kcp ? toCamelCase(kcp.getName()) : abstractProperty.getName()
            ))
            .sorted((first, second) -> {
                // names are still camelCase at this point, KebabCaseProperty
                // wraps them after sorting, so sort on the original name
                final String aName = first instanceof KebabCaseProperty kcp
                    ? toCamelCase(kcp.getName()) : first.getName();
                final String bName = second instanceof KebabCaseProperty kcp
                    ? toCamelCase(kcp.getName()) : second.getName();
                int ai = declarationOrder.indexOf(aName);
                int bi = declarationOrder.indexOf(bName);
                // unknown fields go to the end
                if (ai == -1) ai = Integer.MAX_VALUE;
                if (bi == -1) bi = Integer.MAX_VALUE;
                return Integer.compare(ai, bi);
            })
            .map(abstractProperty -> abstractProperty instanceof KebabCaseProperty ? abstractProperty : new KebabCaseProperty(abstractProperty))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Property getProperty(final Class<?> type, final String name) {
        final Map<String, Property> properties = getPropertiesMap(type, BeanAccess.FIELD);
        final Property property = properties.get(toCamelCase(name));
        if (property == null) {
            throw new YAMLException("Unable to find property '" + name + "' on class: " + type.getName());
        }
        return property;
    }

    private static final class KebabCaseProperty extends GenericProperty {

        private static final Field GENERIC_TYPE_FIELD;

        static {
            try {
                GENERIC_TYPE_FIELD = GenericProperty.class.getDeclaredField("genType");
                GENERIC_TYPE_FIELD.setAccessible(true);
            } catch (final NoSuchFieldException nsfe) {
                throw new ExceptionInInitializerError(nsfe);
            }
        }

        private final Property delegate;

        KebabCaseProperty(final Property delegate) {
            super(toKebabCase(delegate.getName()), delegate.getType(), resolveGenericType(delegate));
            this.delegate = delegate;
        }

        @Override
        public void set(final Object object, final Object value) throws Exception {
            delegate.set(object, value);
        }

        @Override
        public Object get(final Object object) {
            return delegate.get(object);
        }

        @Override
        public List<Annotation> getAnnotations() {
            return delegate.getAnnotations();
        }

        @Override
        public <A extends Annotation> A getAnnotation(final Class<A> annotationType) {
            return delegate.getAnnotation(annotationType);
        }

        @Nullable
        private static Type resolveGenericType(final Property delegate) {
            if (delegate instanceof GenericProperty) {
                try {
                    return (Type) GENERIC_TYPE_FIELD.get(delegate);
                } catch (final IllegalAccessException ignored) {
                }
            }
            return null;
        }
    }
}
