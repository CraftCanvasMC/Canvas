//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public abstract class CollectionStartEvent extends NodeEvent {
    private final String tag;
    private final boolean implicit;
    private final FlowStyle flowStyle;

    public CollectionStartEvent(String anchor, String tag, boolean implicit, Mark startMark, Mark endMark, FlowStyle flowStyle) {
        super(anchor, startMark, endMark);
        this.tag = tag;
        this.implicit = implicit;
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
    public CollectionStartEvent(String anchor, String tag, boolean implicit, Mark startMark, Mark endMark, Boolean flowStyle) {
        this(anchor, tag, implicit, startMark, endMark, FlowStyle.fromBoolean(flowStyle));
    }

    public String getTag() {
        return this.tag;
    }

    public boolean getImplicit() {
        return this.implicit;
    }

    public FlowStyle getFlowStyle() {
        return this.flowStyle;
    }

    protected String getArguments() {
        return super.getArguments() + ", tag=" + this.tag + ", implicit=" + this.implicit;
    }

    public boolean isFlow() {
        return FlowStyle.FLOW == this.flowStyle;
    }
}
