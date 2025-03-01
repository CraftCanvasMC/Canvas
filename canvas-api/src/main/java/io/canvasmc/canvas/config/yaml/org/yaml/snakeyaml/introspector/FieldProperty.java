//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.ArrayUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

public class FieldProperty extends GenericProperty {
    private final Field field;

    public FieldProperty(Field field) {
        super(field.getName(), field.getType(), field.getGenericType());
        this.field = field;
        field.setAccessible(true);
    }

    public void set(Object object, Object value) throws Exception {
        this.field.set(object, value);
    }

    public Object get(Object object) {
        try {
            return this.field.get(object);
        } catch (Exception e) {
            throw new YAMLException("Unable to access field " + this.field.getName() + " on object " + object + " : " + e);
        }
    }

    public List<Annotation> getAnnotations() {
        return ArrayUtils.toUnmodifiableList(this.field.getAnnotations());
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return this.field.getAnnotation(annotationType);
    }
}
