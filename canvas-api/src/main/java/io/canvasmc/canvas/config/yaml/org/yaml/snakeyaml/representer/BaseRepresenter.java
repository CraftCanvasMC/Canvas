//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.introspector.PropertyUtils;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.AnchorNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.MappingNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.ScalarNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.SequenceNode;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseRepresenter {
    protected final Map<Class<?>, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent> representers = new HashMap();
    protected final Map<Class<?>, io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent> multiRepresenters = new LinkedHashMap();
    protected final Map<Object, Node> representedObjects;
    protected io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent nullRepresenter;
    protected ScalarStyle defaultScalarStyle = null;
    protected FlowStyle defaultFlowStyle;
    protected Object objectToRepresent;
    private PropertyUtils propertyUtils;
    private boolean explicitPropertyUtils;

    public BaseRepresenter() {
        this.defaultFlowStyle = FlowStyle.AUTO;
        this.representedObjects = new IdentityHashMap<Object, Node>() {
            private static final long serialVersionUID = -5576159264232131854L;

            public Node put(Object key, Node value) {
                return super.put(key, new AnchorNode(value));
            }
        };
        this.explicitPropertyUtils = false;
    }

    public Node represent(Object data) {
        Node node = this.representData(data);
        this.representedObjects.clear();
        this.objectToRepresent = null;
        return node;
    }

    protected final Node representData(Object data) {
        this.objectToRepresent = data;
        if (this.representedObjects.containsKey(this.objectToRepresent)) {
            Node node = this.representedObjects.get(this.objectToRepresent);
            return node;
        } else if (data == null) {
            Node node = this.nullRepresenter.representData(null);
            return node;
        } else {
            Class<?> clazz = data.getClass();
            Node node;
            if (this.representers.containsKey(clazz)) {
                io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent representer = this.representers.get(clazz);
                node = representer.representData(data);
            } else {
                for (Class<?> repr : this.multiRepresenters.keySet()) {
                    if (repr != null && repr.isInstance(data)) {
                        io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent representer = this.multiRepresenters.get(repr);
                        node = representer.representData(data);
                        return node;
                    }
                }

                if (this.multiRepresenters.containsKey(null)) {
                    io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent representer = this.multiRepresenters.get(null);
                    node = representer.representData(data);
                } else {
                    io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.representer.Represent representer = this.representers.get(null);
                    node = representer.representData(data);
                }
            }

            return node;
        }
    }

    protected Node representScalar(Tag tag, String value, ScalarStyle style) {
        if (style == null) {
            style = this.defaultScalarStyle;
        }

        Node node = new ScalarNode(tag, value, null, null, style);
        return node;
    }

    protected Node representScalar(Tag tag, String value) {
        return this.representScalar(tag, value, null);
    }

    protected Node representSequence(Tag tag, Iterable<?> sequence, FlowStyle flowStyle) {
        int size = 10;
        if (sequence instanceof List) {
            size = ((List) sequence).size();
        }

        List<Node> value = new ArrayList(size);
        SequenceNode node = new SequenceNode(tag, value, flowStyle);
        this.representedObjects.put(this.objectToRepresent, node);
        FlowStyle bestStyle = FlowStyle.FLOW;

        for (Object item : sequence) {
            Node nodeItem = this.representData(item);
            if (!(nodeItem instanceof ScalarNode) || !((ScalarNode) nodeItem).isPlain()) {
                bestStyle = FlowStyle.BLOCK;
            }

            value.add(nodeItem);
        }

        if (flowStyle == FlowStyle.AUTO) {
            if (this.defaultFlowStyle != FlowStyle.AUTO) {
                node.setFlowStyle(this.defaultFlowStyle);
            } else {
                node.setFlowStyle(bestStyle);
            }
        }

        return node;
    }

    protected Node representMapping(Tag tag, Map<?, ?> mapping, FlowStyle flowStyle) {
        List<NodeTuple> value = new ArrayList(mapping.size());
        MappingNode node = new MappingNode(tag, value, flowStyle);
        this.representedObjects.put(this.objectToRepresent, node);
        FlowStyle bestStyle = FlowStyle.FLOW;

        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            Node nodeKey = this.representData(entry.getKey());
            Node nodeValue = this.representData(entry.getValue());
            if (!(nodeKey instanceof ScalarNode) || !((ScalarNode) nodeKey).isPlain()) {
                bestStyle = FlowStyle.BLOCK;
            }

            if (!(nodeValue instanceof ScalarNode) || !((ScalarNode) nodeValue).isPlain()) {
                bestStyle = FlowStyle.BLOCK;
            }

            value.add(new NodeTuple(nodeKey, nodeValue));
        }

        if (flowStyle == FlowStyle.AUTO) {
            if (this.defaultFlowStyle != FlowStyle.AUTO) {
                node.setFlowStyle(this.defaultFlowStyle);
            } else {
                node.setFlowStyle(bestStyle);
            }
        }

        return node;
    }

    public ScalarStyle getDefaultScalarStyle() {
        return this.defaultScalarStyle == null ? ScalarStyle.PLAIN : this.defaultScalarStyle;
    }

    public void setDefaultScalarStyle(ScalarStyle defaultStyle) {
        this.defaultScalarStyle = defaultStyle;
    }

    public FlowStyle getDefaultFlowStyle() {
        return this.defaultFlowStyle;
    }

    public void setDefaultFlowStyle(FlowStyle defaultFlowStyle) {
        this.defaultFlowStyle = defaultFlowStyle;
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
    }

    public final boolean isExplicitPropertyUtils() {
        return this.explicitPropertyUtils;
    }
}
