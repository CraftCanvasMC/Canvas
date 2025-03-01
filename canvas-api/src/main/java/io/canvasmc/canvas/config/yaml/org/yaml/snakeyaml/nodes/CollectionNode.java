//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;
import java.util.List;

public abstract class CollectionNode<T> extends Node {
    private FlowStyle flowStyle;

    public CollectionNode(io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.nodes.Tag tag, Mark startMark, Mark endMark, FlowStyle flowStyle) {
        super(tag, startMark, endMark);
        this.setFlowStyle(flowStyle);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public CollectionNode(Tag tag, Mark startMark, Mark endMark, Boolean flowStyle) {
        this(tag, startMark, endMark, FlowStyle.fromBoolean(flowStyle));
    }

    public abstract List<T> getValue();

    public FlowStyle getFlowStyle() {
        return this.flowStyle;
    }

    public void setFlowStyle(FlowStyle flowStyle) {
        if (flowStyle == null) {
            throw new NullPointerException("Flow style must be provided.");
        } else {
            this.flowStyle = flowStyle;
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setFlowStyle(Boolean flowStyle) {
        this.setFlowStyle(FlowStyle.fromBoolean(flowStyle));
    }

    public void setEndMark(Mark endMark) {
        this.endMark = endMark;
    }
}
