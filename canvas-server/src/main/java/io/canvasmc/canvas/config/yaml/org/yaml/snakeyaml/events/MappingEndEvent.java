//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.error.Mark;

public final class MappingEndEvent extends CollectionEndEvent {
    public MappingEndEvent(Mark startMark, Mark endMark) {
        super(startMark, endMark);
    }

    public ID getEventId() {
        return ID.MappingEnd;
    }
}
