package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.FieldProperty;
import org.yaml.snakeyaml.introspector.GenericProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class FieldOrderPropertyUtils extends PropertyUtils {

    public FieldOrderPropertyUtils() {
        setBeanAccess(BeanAccess.FIELD);
    }

    static String toKebabCase(final String camel) {
        if (camel == null || camel.isEmpty()) return camel;

        StringBuilder sb = new StringBuilder(camel.length() + 4);
        char[] chars = camel.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                boolean prevIsLower = i > 0 && !Character.isUpperCase(chars[i - 1]);
                boolean nextIsLower = i + 1 < chars.length && Character.isLowerCase(chars[i + 1]);
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

    static String fromKebabCase(final String kebab) {
        if (kebab == null || !kebab.contains("-")) return kebab;

        StringBuilder sb = new StringBuilder(kebab.length());
        boolean nextUpper = false;

        for (char c : kebab.toCharArray()) {
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
    protected Set<Property> createPropertySet(Class<?> type, BeanAccess bAccess) {
        // get the default property set from snakeyaml
        Collection<Property> properties = super.createPropertySet(type, bAccess);

        // build a declaration-order index from reflection
        // getDeclaredFields returns fields in declaration order per the JVM spec
        // we walk the hierarchy too so nested/inherited fields sort correctly

        // filter out any fields declared in Part itself
        Set<String> partFields = Arrays.stream(Part.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        List<String> declarationOrder = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Arrays.stream(current.getDeclaredFields())
                .filter(f -> !f.accessFlags().contains(AccessFlag.STATIC))
                .map(Field::getName)
                .filter(name -> !partFields.contains(name))
                .forEach(declarationOrder::add);
            current = current.getSuperclass();
        }

        return properties.stream()
            .filter(p -> {
                // the initial filter should filter anything final
                if (p instanceof FieldProperty fieldProperty) {
                    try {
                        Field fieldToGetTheFieldObjectOfTheFieldProperty = FieldProperty.class.getDeclaredField("field");
                        fieldToGetTheFieldObjectOfTheFieldProperty.setAccessible(true);
                        Field field = (Field) fieldToGetTheFieldObjectOfTheFieldProperty.get(fieldProperty);

                        // fields with final modifiers should be ignored from the configurations
                        if (field.accessFlags().contains(AccessFlag.FINAL)) {
                            return false;
                        }
                    } catch (NoSuchFieldException nsfe) {
                        throw new RuntimeException("field field not found? Where the field field go?", nsfe);
                    } catch (IllegalAccessException iae) {
                        throw new RuntimeException("Illegal access to field :(", iae);
                    }
                }
                // the field shouldn't be final, or not field property! yippeee :)
                return true;
            })
            .filter(p -> !partFields.contains(
                p instanceof KebabCaseProperty kcp ? fromKebabCase(kcp.getName()) : p.getName()
            ))
            .sorted((a, b) -> {
                // names are still camelCase at this point, KebabCaseProperty
                // wraps them after sorting, so sort on the original name
                String aName = a instanceof KebabCaseProperty kcp
                    ? fromKebabCase(kcp.getName()) : a.getName();
                String bName = b instanceof KebabCaseProperty kcp
                    ? fromKebabCase(kcp.getName()) : b.getName();
                int ai = declarationOrder.indexOf(aName);
                int bi = declarationOrder.indexOf(bName);
                // unknown fields go to the end
                if (ai == -1) ai = Integer.MAX_VALUE;
                if (bi == -1) bi = Integer.MAX_VALUE;
                return Integer.compare(ai, bi);
            })
            .map(p -> p instanceof KebabCaseProperty ? p : new KebabCaseProperty(p))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Property getProperty(Class<?> type, String name) {
        Map<String, Property> properties = getPropertiesMap(type, BeanAccess.FIELD);
        Property property = properties.get(fromKebabCase(name));
        if (property == null) {
            throw new YAMLException(
                "Unable to find property '" + name + "' on class: " + type.getName());
        }
        return property;
    }

    private static final class KebabCaseProperty extends GenericProperty {

        private static final Field GENERIC_TYPE_FIELD;

        static {
            try {
                GENERIC_TYPE_FIELD = GenericProperty.class.getDeclaredField("genType");
                GENERIC_TYPE_FIELD.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final Property delegate;

        private static Type resolveGenericType(final Property delegate) {
            if (delegate instanceof GenericProperty) {
                try {
                    return (Type) GENERIC_TYPE_FIELD.get(delegate);
                } catch (IllegalAccessException ignored) {
                }
            }
            return null;
        }

        KebabCaseProperty(final @NonNull Property delegate) {
            super(toKebabCase(delegate.getName()), delegate.getType(), resolveGenericType(delegate));
            this.delegate = delegate;
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            delegate.set(object, value);
        }

        @Override
        public Object get(Object object) {
            return delegate.get(object);
        }

        @Override
        public List<java.lang.annotation.Annotation> getAnnotations() {
            return delegate.getAnnotations();
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            return delegate.getAnnotation(annotationType);
        }
    }
}
