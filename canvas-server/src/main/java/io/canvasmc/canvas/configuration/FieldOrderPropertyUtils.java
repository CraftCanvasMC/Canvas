package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class FieldOrderPropertyUtils extends PropertyUtils {

    public FieldOrderPropertyUtils() {
        setBeanAccess(BeanAccess.FIELD);
    }

    @Override
    protected Set<Property> createPropertySet(Class<?> type, BeanAccess bAccess) {
        // get the default property set from snakeyaml
        Collection<Property> properties = super.createPropertySet(type, bAccess);

        // build a declaration-order index from reflection
        // getDeclaredFields returns fields in declaration order per the JVM spec
        // we walk the hierarchy too so nested/inherited fields sort correctly

        List<String> declarationOrder = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Arrays.stream(current.getDeclaredFields())
                .filter(f -> !f.accessFlags().contains(AccessFlag.STATIC))
                .map(Field::getName)
                .forEach(declarationOrder::add);
            current = current.getSuperclass();
        }

        return properties.stream()
            .sorted((a, b) -> {
                int ai = declarationOrder.indexOf(a.getName());
                int bi = declarationOrder.indexOf(b.getName());
                // unknown fields go to the end
                if (ai == -1) ai = Integer.MAX_VALUE;
                if (bi == -1) bi = Integer.MAX_VALUE;
                return Integer.compare(ai, bi);
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
