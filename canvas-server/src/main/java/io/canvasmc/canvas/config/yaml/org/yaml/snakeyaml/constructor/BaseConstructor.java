//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.LoaderOptions;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.TypeDescription;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.composer.Composer;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.YAMLException;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.PropertyUtils;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.CollectionNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.MappingNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeId;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.ScalarNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.SequenceNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public abstract class BaseConstructor {
    protected final Map<NodeId, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct> yamlClassConstructors;
    protected final Map<Tag, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct> yamlConstructors;
    protected final Map<String, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct> yamlMultiConstructors;
    protected final Map<Class<? extends Object>, TypeDescription> typeDefinitions;
    protected final Map<Tag, Class<? extends Object>> typeTags;
    final Map<Node, Object> constructedObjects;
    private final Set<Node> recursiveObjects;
    private final ArrayList<RecursiveTuple<Map<Object, Object>, RecursiveTuple<Object, Object>>> maps2fill;
    private final ArrayList<RecursiveTuple<Set<Object>, Object>> sets2fill;
    protected Composer composer;
    protected Tag rootTag;
    protected LoaderOptions loadingConfig;
    private PropertyUtils propertyUtils;
    private boolean explicitPropertyUtils;
    private boolean allowDuplicateKeys;
    private boolean wrappedToRootException;

    public BaseConstructor() {
        this(new LoaderOptions());
    }

    public BaseConstructor(LoaderOptions loadingConfig) {
        this.yamlClassConstructors = new EnumMap(NodeId.class);
        this.yamlConstructors = new HashMap();
        this.yamlMultiConstructors = new HashMap();
        this.allowDuplicateKeys = true;
        this.wrappedToRootException = false;
        this.constructedObjects = new HashMap();
        this.recursiveObjects = new HashSet();
        this.maps2fill = new ArrayList();
        this.sets2fill = new ArrayList();
        this.typeDefinitions = new HashMap();
        this.typeTags = new HashMap();
        this.rootTag = null;
        this.explicitPropertyUtils = false;
        this.typeDefinitions.put(SortedMap.class, new TypeDescription(SortedMap.class, Tag.OMAP, TreeMap.class));
        this.typeDefinitions.put(SortedSet.class, new TypeDescription(SortedSet.class, Tag.SET, TreeSet.class));
        this.loadingConfig = loadingConfig;
    }

    public void setComposer(Composer composer) {
        this.composer = composer;
    }

    public boolean checkData() {
        return this.composer.checkNode();
    }

    public Object getData() throws NoSuchElementException {
        if (!this.composer.checkNode()) {
            throw new NoSuchElementException("No document is available.");
        } else {
            Node node = this.composer.getNode();
            if (this.rootTag != null) {
                node.setTag(this.rootTag);
            }

            return this.constructDocument(node);
        }
    }

    public Object getSingleData(Class<?> type) {
        Node node = this.composer.getSingleNode();
        if (node != null && !Tag.NULL.equals(node.getTag())) {
            if (Object.class != type) {
                node.setTag(new Tag(type));
            } else if (this.rootTag != null) {
                node.setTag(this.rootTag);
            }

            return this.constructDocument(node);
        } else {
            io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct construct = this.yamlConstructors.get(Tag.NULL);
            return construct.construct(node);
        }
    }

    protected final Object constructDocument(Node node) {
        Object var3;
        try {
            Object data = this.constructObject(node);
            this.fillRecursive();
            var3 = data;
        } catch (RuntimeException var7) {
            if (this.wrappedToRootException && !(var7 instanceof YAMLException)) {
                throw new YAMLException(var7);
            }

            throw var7;
        } finally {
            this.constructedObjects.clear();
            this.recursiveObjects.clear();
        }

        return var3;
    }

    private void fillRecursive() {
        if (!this.maps2fill.isEmpty()) {
            for (RecursiveTuple<Map<Object, Object>, RecursiveTuple<Object, Object>> entry : this.maps2fill) {
                RecursiveTuple<Object, Object> key_value = entry._2();
                entry._1().put(key_value._1(), key_value._2());
            }

            this.maps2fill.clear();
        }

        if (!this.sets2fill.isEmpty()) {
            for (RecursiveTuple<Set<Object>, Object> value : this.sets2fill) {
                value._1().add(value._2());
            }

            this.sets2fill.clear();
        }

    }

    protected Object constructObject(Node node) {
        return this.constructedObjects.containsKey(node) ? this.constructedObjects.get(node) : this.constructObjectNoCheck(node);
    }

    protected Object constructObjectNoCheck(Node node) {
        if (this.recursiveObjects.contains(node)) {
            throw new ConstructorException(null, null, "found unconstructable recursive node", node.getStartMark());
        } else {
            this.recursiveObjects.add(node);
            io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct constructor = this.getConstructor(node);
            Object data = this.constructedObjects.containsKey(node) ? this.constructedObjects.get(node) : constructor.construct(node);
            this.finalizeConstruction(node, data);
            this.constructedObjects.put(node, data);
            this.recursiveObjects.remove(node);
            if (node.isTwoStepsConstruction()) {
                constructor.construct2ndStep(node, data);
            }

            return data;
        }
    }

    protected io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct getConstructor(Node node) {
        if (node.useClassConstructor()) {
            return this.yamlClassConstructors.get(node.getNodeId());
        } else {
            io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.constructor.Construct constructor = this.yamlConstructors.get(node.getTag());
            if (constructor == null) {
                for (String prefix : this.yamlMultiConstructors.keySet()) {
                    if (node.getTag().startsWith(prefix)) {
                        return this.yamlMultiConstructors.get(prefix);
                    }
                }

                return this.yamlConstructors.get(null);
            } else {
                return constructor;
            }
        }
    }

    protected String constructScalar(ScalarNode node) {
        return node.getValue();
    }

    protected List<Object> createDefaultList(int initSize) {
        return new ArrayList(initSize);
    }

    protected Set<Object> createDefaultSet(int initSize) {
        return new LinkedHashSet(initSize);
    }

    protected Map<Object, Object> createDefaultMap(int initSize) {
        return new LinkedHashMap(initSize);
    }

    protected Object createArray(Class<?> type, int size) {
        return Array.newInstance(type.getComponentType(), size);
    }

    protected Object finalizeConstruction(Node node, Object data) {
        Class<? extends Object> type = node.getType();
        return this.typeDefinitions.containsKey(type) ? this.typeDefinitions.get(type).finalizeConstruction(data) : data;
    }

    protected Object newInstance(Node node) {
        try {
            return this.newInstance(Object.class, node);
        } catch (InstantiationException e) {
            throw new YAMLException(e);
        }
    }

    protected final Object newInstance(Class<?> ancestor, Node node) throws InstantiationException {
        return this.newInstance(ancestor, node, true);
    }

    protected Object newInstance(Class<?> ancestor, Node node, boolean tryDefault) throws InstantiationException {
        Class<? extends Object> type = node.getType();
        if (this.typeDefinitions.containsKey(type)) {
            TypeDescription td = this.typeDefinitions.get(type);
            Object instance = td.newInstance(node);
            if (instance != null) {
                return instance;
            }
        }

        if (tryDefault && ancestor.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
            try {
                Constructor<?> c = type.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException e) {
                throw new InstantiationException("NoSuchMethodException:" + e.getLocalizedMessage());
            } catch (Exception e) {
                throw new YAMLException(e);
            }
        } else {
            throw new InstantiationException();
        }
    }

    protected Set<Object> newSet(CollectionNode<?> node) {
        try {
            return (Set) this.newInstance(Set.class, node);
        } catch (InstantiationException var3) {
            return this.createDefaultSet(node.getValue().size());
        }
    }

    protected List<Object> newList(SequenceNode node) {
        try {
            return (List) this.newInstance(List.class, node);
        } catch (InstantiationException var3) {
            return this.createDefaultList(node.getValue().size());
        }
    }

    protected Map<Object, Object> newMap(MappingNode node) {
        try {
            return (Map) this.newInstance(Map.class, node);
        } catch (InstantiationException var3) {
            return this.createDefaultMap(node.getValue().size());
        }
    }

    protected List<? extends Object> constructSequence(SequenceNode node) {
        List<Object> result = this.newList(node);
        this.constructSequenceStep2(node, result);
        return result;
    }

    protected Set<? extends Object> constructSet(SequenceNode node) {
        Set<Object> result = this.newSet(node);
        this.constructSequenceStep2(node, result);
        return result;
    }

    protected Object constructArray(SequenceNode node) {
        return this.constructArrayStep2(node, this.createArray(node.getType(), node.getValue().size()));
    }

    protected void constructSequenceStep2(SequenceNode node, Collection<Object> collection) {
        for (Node child : node.getValue()) {
            collection.add(this.constructObject(child));
        }

    }

    protected Object constructArrayStep2(SequenceNode node, Object array) {
        Class<?> componentType = node.getType().getComponentType();
        int index = 0;

        for (Node child : node.getValue()) {
            if (child.getType() == Object.class) {
                child.setType(componentType);
            }

            Object value = this.constructObject(child);
            if (componentType.isPrimitive()) {
                if (value == null) {
                    throw new NullPointerException("Unable to construct element value for " + child);
                }

                if (Byte.TYPE.equals(componentType)) {
                    Array.setByte(array, index, ((Number) value).byteValue());
                } else if (Short.TYPE.equals(componentType)) {
                    Array.setShort(array, index, ((Number) value).shortValue());
                } else if (Integer.TYPE.equals(componentType)) {
                    Array.setInt(array, index, ((Number) value).intValue());
                } else if (Long.TYPE.equals(componentType)) {
                    Array.setLong(array, index, ((Number) value).longValue());
                } else if (Float.TYPE.equals(componentType)) {
                    Array.setFloat(array, index, ((Number) value).floatValue());
                } else if (Double.TYPE.equals(componentType)) {
                    Array.setDouble(array, index, ((Number) value).doubleValue());
                } else if (Character.TYPE.equals(componentType)) {
                    Array.setChar(array, index, (Character) value);
                } else {
                    if (!Boolean.TYPE.equals(componentType)) {
                        throw new YAMLException("unexpected primitive type");
                    }

                    Array.setBoolean(array, index, (Boolean) value);
                }
            } else {
                Array.set(array, index, value);
            }

            ++index;
        }

        return array;
    }

    protected Set<Object> constructSet(MappingNode node) {
        Set<Object> set = this.newSet(node);
        this.constructSet2ndStep(node, set);
        return set;
    }

    protected Map<Object, Object> constructMapping(MappingNode node) {
        Map<Object, Object> mapping = this.newMap(node);
        this.constructMapping2ndStep(node, mapping);
        return mapping;
    }

    protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();
            Object key = this.constructObject(keyNode);
            if (key != null) {
                try {
                    key.hashCode();
                } catch (Exception e) {
                    throw new ConstructorException("while constructing a mapping", node.getStartMark(), "found unacceptable key " + key, tuple.getKeyNode()
                                                                                                                                              .getStartMark(), e);
                }
            }

            Object value = this.constructObject(valueNode);
            if (keyNode.isTwoStepsConstruction()) {
                if (!this.loadingConfig.getAllowRecursiveKeys()) {
                    throw new YAMLException("Recursive key for mapping is detected but it is not configured to be allowed.");
                }

                this.postponeMapFilling(mapping, key, value);
            } else {
                mapping.put(key, value);
            }
        }

    }

    protected void postponeMapFilling(Map<Object, Object> mapping, Object key, Object value) {
        this.maps2fill.add(0, new RecursiveTuple(mapping, new RecursiveTuple(key, value)));
    }

    protected void constructSet2ndStep(MappingNode node, Set<Object> set) {
        for (NodeTuple tuple : node.getValue()) {
            Node keyNode = tuple.getKeyNode();
            Object key = this.constructObject(keyNode);
            if (key != null) {
                try {
                    key.hashCode();
                } catch (Exception e) {
                    throw new ConstructorException("while constructing a Set", node.getStartMark(), "found unacceptable key " + key, tuple.getKeyNode()
                                                                                                                                          .getStartMark(), e);
                }
            }

            if (keyNode.isTwoStepsConstruction()) {
                this.postponeSetFilling(set, key);
            } else {
                set.add(key);
            }
        }

    }

    protected void postponeSetFilling(Set<Object> set, Object key) {
        this.sets2fill.add(0, new RecursiveTuple(set, key));
    }

    public final PropertyUtils getPropertyUtils() {
        if (this.propertyUtils == null) {
            this.propertyUtils = new PropertyUtils();
        }

        return this.propertyUtils;
    }

    public void setPropertyUtils(PropertyUtils propertyUtils) {
        this.propertyUtils = propertyUtils;
        this.explicitPropertyUtils = true;

        for (TypeDescription typeDescription : this.typeDefinitions.values()) {
            typeDescription.setPropertyUtils(propertyUtils);
        }

    }

    public TypeDescription addTypeDescription(TypeDescription definition) {
        if (definition == null) {
            throw new NullPointerException("TypeDescription is required.");
        } else {
            Tag tag = definition.getTag();
            this.typeTags.put(tag, definition.getType());
            definition.setPropertyUtils(this.getPropertyUtils());
            return this.typeDefinitions.put(definition.getType(), definition);
        }
    }

    public final boolean isExplicitPropertyUtils() {
        return this.explicitPropertyUtils;
    }

    public boolean isAllowDuplicateKeys() {
        return this.allowDuplicateKeys;
    }

    public void setAllowDuplicateKeys(boolean allowDuplicateKeys) {
        this.allowDuplicateKeys = allowDuplicateKeys;
    }

    public boolean isWrappedToRootException() {
        return this.wrappedToRootException;
    }

    public void setWrappedToRootException(boolean wrappedToRootException) {
        this.wrappedToRootException = wrappedToRootException;
    }

    private static class RecursiveTuple<T, K> {
        private final T _1;
        private final K _2;

        public RecursiveTuple(T _1, K _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public K _2() {
            return this._2;
        }

        public T _1() {
            return this._1;
        }
    }
}
