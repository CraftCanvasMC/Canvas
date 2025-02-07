//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.util.PlatformFeatureDetector;
import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PropertyUtils {
    private static final String TRANSIENT = "transient";
    private final Map<Class<?>, Map<String, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property>> propertiesCache;
    private final Map<Class<?>, Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property>> readableProperties;
    private final PlatformFeatureDetector platformFeatureDetector;
    private io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess beanAccess;
    private boolean allowReadOnlyProperties;
    private boolean skipMissingProperties;

    public PropertyUtils() {
        this(new PlatformFeatureDetector());
    }

    PropertyUtils(PlatformFeatureDetector platformFeatureDetector) {
        this.propertiesCache = new HashMap();
        this.readableProperties = new HashMap();
        this.beanAccess = io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess.DEFAULT;
        this.allowReadOnlyProperties = false;
        this.skipMissingProperties = false;
        this.platformFeatureDetector = platformFeatureDetector;
        if (platformFeatureDetector.isRunningOnAndroid()) {
            this.beanAccess = io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess.FIELD;
        }

    }

    protected Map<String, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> getPropertiesMap(Class<?> type, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess bAccess) {
        if (this.propertiesCache.containsKey(type)) {
            return this.propertiesCache.get(type);
        } else {
            Map<String, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> properties = new LinkedHashMap();
            boolean inaccessableFieldsExist = false;
            switch (bAccess) {
                case FIELD:
                    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                        for (Field field : c.getDeclaredFields()) {
                            int modifiers = field.getModifiers();
                            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !properties.containsKey(field.getName())) {
                                properties.put(field.getName(), new io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.FieldProperty(field));
                            }
                        }
                    }
                    break;
                default:
                    try {
                        for (PropertyDescriptor property : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                            Method readMethod = property.getReadMethod();
                            if ((readMethod == null || !readMethod.getName().equals("getClass")) && !this.isTransient(property)) {
                                properties.put(property.getName(), new MethodProperty(property));
                            }
                        }
                    } catch (IntrospectionException e) {
                        throw new YAMLException(e);
                    }

                    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                        for (Field field : c.getDeclaredFields()) {
                            int modifiers = field.getModifiers();
                            if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                                if (Modifier.isPublic(modifiers)) {
                                    properties.put(field.getName(), new FieldProperty(field));
                                } else {
                                    inaccessableFieldsExist = true;
                                }
                            }
                        }
                    }
            }

            if (properties.isEmpty() && inaccessableFieldsExist) {
                throw new YAMLException("No JavaBean properties found in " + type.getName());
            } else {
                this.propertiesCache.put(type, properties);
                return properties;
            }
        }
    }

    private boolean isTransient(FeatureDescriptor fd) {
        return Boolean.TRUE.equals(fd.getValue("transient"));
    }

    public Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> getProperties(Class<? extends Object> type) {
        return this.getProperties(type, this.beanAccess);
    }

    public Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> getProperties(Class<? extends Object> type, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess bAccess) {
        if (this.readableProperties.containsKey(type)) {
            return this.readableProperties.get(type);
        } else {
            Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> properties = this.createPropertySet(type, bAccess);
            this.readableProperties.put(type, properties);
            return properties;
        }
    }

    protected Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> createPropertySet(Class<? extends Object> type, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess bAccess) {
        Set<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> properties = new TreeSet();

        for (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property property : this.getPropertiesMap(type, bAccess).values()) {
            if (property.isReadable() && (this.allowReadOnlyProperties || property.isWritable())) {
                properties.add(property);
            }
        }

        return properties;
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property getProperty(Class<? extends Object> type, String name) {
        return this.getProperty(type, name, this.beanAccess);
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property getProperty(Class<? extends Object> type, String name, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess bAccess) {
        Map<String, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property> properties = this.getPropertiesMap(type, bAccess);
        io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property property = properties.get(name);
        if (property == null && this.skipMissingProperties) {
            property = new MissingProperty(name);
        }

        if (property == null) {
            throw new YAMLException("Unable to find property '" + name + "' on class: " + type.getName());
        } else {
            return property;
        }
    }

    public void setBeanAccess(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess beanAccess) {
        if (this.platformFeatureDetector.isRunningOnAndroid() && beanAccess != BeanAccess.FIELD) {
            throw new IllegalArgumentException("JVM is Android - only BeanAccess.FIELD is available");
        } else {
            if (this.beanAccess != beanAccess) {
                this.beanAccess = beanAccess;
                this.propertiesCache.clear();
                this.readableProperties.clear();
            }

        }
    }

    public boolean isAllowReadOnlyProperties() {
        return this.allowReadOnlyProperties;
    }

    public void setAllowReadOnlyProperties(boolean allowReadOnlyProperties) {
        if (this.allowReadOnlyProperties != allowReadOnlyProperties) {
            this.allowReadOnlyProperties = allowReadOnlyProperties;
            this.readableProperties.clear();
        }

    }

    public boolean isSkipMissingProperties() {
        return this.skipMissingProperties;
    }

    public void setSkipMissingProperties(boolean skipMissingProperties) {
        if (this.skipMissingProperties != skipMissingProperties) {
            this.skipMissingProperties = skipMissingProperties;
            this.readableProperties.clear();
        }

    }
}
