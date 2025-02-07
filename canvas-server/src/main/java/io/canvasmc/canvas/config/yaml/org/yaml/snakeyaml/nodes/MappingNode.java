//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import java.util.List;

public class MappingNode extends CollectionNode<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> {
    private List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> value;
    private boolean merged;

    public MappingNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, boolean resolved, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> value, Mark startMark, Mark endMark, FlowStyle flowStyle) {
        super(tag, startMark, endMark, flowStyle);
        this.merged = false;
        if (value == null) {
            throw new NullPointerException("value in a Node is required.");
        } else {
            this.value = value;
            this.resolved = resolved;
        }
    }

    public MappingNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> value, FlowStyle flowStyle) {
        this(tag, true, value, null, null, flowStyle);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public MappingNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, boolean resolved, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> value, Mark startMark, Mark endMark, Boolean flowStyle) {
        this(tag, resolved, value, startMark, endMark, FlowStyle.fromBoolean(flowStyle));
    }

    /**
     * @deprecated
     */
    @Deprecated
    public MappingNode(Tag tag, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> value, Boolean flowStyle) {
        this(tag, value, FlowStyle.fromBoolean(flowStyle));
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeId getNodeId() {
        return NodeId.mapping;
    }

    public List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> getValue() {
        return this.value;
    }

    public void setValue(List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple> mergedValue) {
        this.value = mergedValue;
    }

    public void setOnlyKeyType(Class<? extends Object> keyType) {
        for (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple nodes : this.value) {
            nodes.getKeyNode().setType(keyType);
        }

    }

    public void setTypes(Class<? extends Object> keyType, Class<? extends Object> valueType) {
        for (io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeTuple nodes : this.value) {
            nodes.getValueNode().setType(valueType);
            nodes.getKeyNode().setType(keyType);
        }

    }

    public String toString() {
        StringBuilder buf = new StringBuilder();

        for (NodeTuple node : this.getValue()) {
            buf.append("{ key=");
            buf.append(node.getKeyNode());
            buf.append("; value=");
            if (node.getValueNode() instanceof CollectionNode) {
                buf.append(System.identityHashCode(node.getValueNode()));
            } else {
                buf.append(node);
            }

            buf.append(" }");
        }

        String values = buf.toString();
        return "<" + this.getClass().getName() + " (tag=" + this.getTag() + ", values=" + values + ")>";
    }

    public boolean isMerged() {
        return this.merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }
}
