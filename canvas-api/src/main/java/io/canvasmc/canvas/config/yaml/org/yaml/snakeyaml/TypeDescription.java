//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.BeanAccess;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.Property;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.PropertySubstitute;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.PropertyUtils;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class TypeDescription {
    private static final Logger log = Logger.getLogger(TypeDescription.class.getPackage().getName());
    private final Class<? extends Object> type;
    protected Set<String> excludes;
    protected String[] includes;
    protected BeanAccess beanAccess;
    private Class<?> impl;
    private Tag tag;
    private transient Set<Property> dumpProperties;
    private transient PropertyUtils propertyUtils;
    private transient boolean delegatesChecked;
    private Map<String, PropertySubstitute> properties;

    public TypeDescription(Class<? extends Object> clazz, Tag tag) {
        this(clazz, tag, null);
    }

    public TypeDescription(Class<? extends Object> clazz, Tag tag, Class<?> impl) {
        this.properties = Collections.emptyMap();
        this.excludes = Collections.emptySet();
        this.includes = null;
        this.type = clazz;
        this.tag = tag;
        this.impl = impl;
        this.beanAccess = null;
    }

    public TypeDescription(Class<? extends Object> clazz, String tag) {
        this(clazz, new Tag(tag), null);
    }

    public TypeDescription(Class<? extends Object> clazz) {
        this(clazz, null, null);
    }

    public TypeDescription(Class<? extends Object> clazz, Class<?> impl) {
        this(clazz, null, impl);
    }

    public Tag getTag() {
        return this.tag;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setTag(Tag tag) {
        this.tag = tag;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setTag(String tag) {
        this.setTag(new Tag(tag));
    }

    public Class<? extends Object> getType() {
        return this.type;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void putListPropertyType(String property, Class<? extends Object> type) {
        this.addPropertyParameters(property, type);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Class<? extends Object> getListPropertyType(String property) {
        if (this.properties.containsKey(property)) {
            Class<?>[] typeArguments = this.properties.get(property).getActualTypeArguments();
            if (typeArguments != null && typeArguments.length > 0) {
                return typeArguments[0];
            }
        }

        return null;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void putMapPropertyType(String property, Class<? extends Object> key, Class<? extends Object> value) {
        this.addPropertyParameters(property, key, value);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Class<? extends Object> getMapKeyType(String property) {
        if (this.properties.containsKey(property)) {
            Class<?>[] typeArguments = this.properties.get(property).getActualTypeArguments();
            if (typeArguments != null && typeArguments.length > 0) {
                return typeArguments[0];
            }
        }

        return null;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Class<? extends Object> getMapValueType(String property) {
        if (this.properties.containsKey(property)) {
            Class<?>[] typeArguments = this.properties.get(property).getActualTypeArguments();
            if (typeArguments != null && typeArguments.length > 1) {
                return typeArguments[1];
            }
        }

        return null;
    }

    public void addPropertyParameters(String pName, Class<?>... classes) {
        if (!this.properties.containsKey(pName)) {
            this.substituteProperty(pName, null, null, null, classes);
        } else {
            PropertySubstitute pr = this.properties.get(pName);
            pr.setActualTypeArguments(classes);
        }

    }

    public String toString() {
        return "TypeDescription for " + this.getType() + " (tag='" + this.getTag() + "')";
    }

    private void checkDelegates() {
        for (PropertySubstitute p : this.properties.values()) {
            try {
                p.setDelegate(this.discoverProperty(p.getName()));
            } catch (YAMLException var5) {
            }
        }

        this.delegatesChecked = true;
    }

    private Property discoverProperty(String name) {
        if (this.propertyUtils != null) {
            return this.beanAccess == null ? this.propertyUtils.getProperty(this.type, name) : this.propertyUtils.getProperty(this.type, name, this.beanAccess);
        } else {
            return null;
        }
    }

    public Property getProperty(String name) {
        if (!this.delegatesChecked) {
            this.checkDelegates();
        }

        return this.properties.containsKey(name) ? this.properties.get(name) : this.discoverProperty(name);
    }

    public void substituteProperty(String pName, Class<?> pType, String getter, String setter, Class<?>... argParams) {
        this.substituteProperty(new PropertySubstitute(pName, pType, getter, setter, argParams));
    }

    public void substituteProperty(PropertySubstitute substitute) {
        if (Collections.EMPTY_MAP == this.properties) {
            this.properties = new LinkedHashMap();
        }

        substitute.setTargetType(this.type);
        this.properties.put(substitute.getName(), substitute);
    }

    public void setPropertyUtils(PropertyUtils propertyUtils) {
        this.propertyUtils = propertyUtils;
    }

    public void setIncludes(String... propNames) {
        this.includes = propNames != null && propNames.length > 0 ? propNames : null;
    }

    public void setExcludes(String... propNames) {
        if (propNames != null && propNames.length > 0) {
            this.excludes = new HashSet();

            Collections.addAll(this.excludes, propNames);
        } else {
            this.excludes = Collections.emptySet();
        }

    }

    public Set<Property> getProperties() {
        if (this.dumpProperties != null) {
            return this.dumpProperties;
        } else if (this.propertyUtils != null) {
            if (this.includes != null) {
                this.dumpProperties = new LinkedHashSet();

                for (String propertyName : this.includes) {
                    if (!this.excludes.contains(propertyName)) {
                        this.dumpProperties.add(this.getProperty(propertyName));
                    }
                }

                return this.dumpProperties;
            } else {
                Set<Property> readableProps = this.beanAccess == null ? this.propertyUtils.getProperties(this.type) : this.propertyUtils.getProperties(this.type, this.beanAccess);
                if (this.properties.isEmpty()) {
                    if (this.excludes.isEmpty()) {
                        return this.dumpProperties = readableProps;
                    } else {
                        this.dumpProperties = new LinkedHashSet();

                        for (Property property : readableProps) {
                            if (!this.excludes.contains(property.getName())) {
                                this.dumpProperties.add(property);
                            }
                        }

                        return this.dumpProperties;
                    }
                } else {
                    if (!this.delegatesChecked) {
                        this.checkDelegates();
                    }

                    this.dumpProperties = new LinkedHashSet();

                    for (Property property : this.properties.values()) {
                        if (!this.excludes.contains(property.getName()) && property.isReadable()) {
                            this.dumpProperties.add(property);
                        }
                    }

                    for (Property property : readableProps) {
                        if (!this.excludes.contains(property.getName())) {
                            this.dumpProperties.add(property);
                        }
                    }

                    return this.dumpProperties;
                }
            }
        } else {
            return null;
        }
    }

    public boolean setupPropertyType(String key, Node valueNode) {
        return false;
    }

    public boolean setProperty(Object targetBean, String propertyName, Object value) throws Exception {
        return false;
    }

    public Object newInstance(Node node) {
        if (this.impl != null) {
            try {
                Constructor<?> c = this.impl.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (Exception e) {
                log.fine(e.getLocalizedMessage());
                this.impl = null;
            }
        }

        return null;
    }

    public Object newInstance(String propertyName, Node node) {
        return null;
    }

    public Object finalizeConstruction(Object obj) {
        return obj;
    }
}
