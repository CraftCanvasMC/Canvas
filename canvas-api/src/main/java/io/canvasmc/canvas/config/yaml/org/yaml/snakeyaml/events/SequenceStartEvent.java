//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.DumperOptions.FlowStyle;
import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class SequenceStartEvent extends CollectionStartEvent {
    public SequenceStartEvent(String anchor, String tag, boolean implicit, Mark startMark, Mark endMark, FlowStyle flowStyle) {
        super(anchor, tag, implicit, startMark, endMark, flowStyle);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public SequenceStartEvent(String anchor, String tag, boolean implicit, Mark startMark, Mark endMark, Boolean flowStyle) {
        this(anchor, tag, implicit, startMark, endMark, FlowStyle.fromBoolean(flowStyle));
    }

    public ID getEventId() {
        return ID.SequenceStart;
    }
}
