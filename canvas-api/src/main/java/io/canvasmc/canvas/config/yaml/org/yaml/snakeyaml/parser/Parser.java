//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.parser;

import io.canvasmc.canvas.config.yaml.org.yaml.snakeyaml.events.Event;

public interface Parser {
    boolean checkEvent(Event.ID var1);

    Event peekEvent();

    Event getEvent();
}
