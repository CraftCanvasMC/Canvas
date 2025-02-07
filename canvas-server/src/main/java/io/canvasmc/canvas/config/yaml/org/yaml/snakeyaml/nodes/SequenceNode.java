//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import java.util.List;

public class SequenceNode extends CollectionNode<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> {
    private final List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> value;

    public SequenceNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, boolean resolved, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> value, Mark startMark, Mark endMark, FlowStyle flowStyle) {
        super(tag, startMark, endMark, flowStyle);
        if (value == null) {
            throw new NullPointerException("value in a Node is required.");
        } else {
            this.value = value;
            this.resolved = resolved;
        }
    }

    public SequenceNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> value, FlowStyle flowStyle) {
        this(tag, true, value, null, null, flowStyle);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public SequenceNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> value, Boolean style) {
        this(tag, value, FlowStyle.fromBoolean(style));
    }

    /**
     * @deprecated
     */
    @Deprecated
    public SequenceNode(Tag tag, boolean resolved, List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> value, Mark startMark, Mark endMark, Boolean style) {
        this(tag, resolved, value, startMark, endMark, FlowStyle.fromBoolean(style));
    }

    public io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.NodeId getNodeId() {
        return NodeId.sequence;
    }

    public List<io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Node> getValue() {
        return this.value;
    }

    public void setListType(Class<? extends Object> listType) {
        for (Node node : this.value) {
            node.setType(listType);
        }

    }

    public String toString() {
        return "<" + this.getClass().getName() + " (tag=" + this.getTag() + ", value=" + this.getValue() + ")>";
    }
}
