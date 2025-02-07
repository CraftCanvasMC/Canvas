//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector;

import java.lang.annotation.Annotation;
import java.util.List;

public abstract class Property implements Comparable<Property> {
    private final String name;
    private final Class<?> type;

    public Property(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    public Class<?> getType() {
        return this.type;
    }

    public abstract Class<?>[] getActualTypeArguments();

    public String getName() {
        return this.name;
    }

    public String toString() {
        return this.getName() + " of " + this.getType();
    }

    public int compareTo(Property o) {
        return this.getName().compareTo(o.getName());
    }

    public boolean isWritable() {
        return true;
    }

    public boolean isReadable() {
        return true;
    }

    public abstract void set(Object var1, Object var2) throws Exception;

    public abstract Object get(Object var1);

    public abstract List<Annotation> getAnnotations();

    public abstract <A extends Annotation> A getAnnotation(Class<A> var1);

    public int hashCode() {
        return this.getName().hashCode() + this.getType().hashCode();
    }

    public boolean equals(Object other) {
        if (!(other instanceof final Property p)) {
            return false;
        } else {
            return this.getName().equals(p.getName()) && this.getType().equals(p.getType());
        }
    }
}
