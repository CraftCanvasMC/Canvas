package io.canvasmc.canvas.server;

import ca.spottedleaf.moonrise.common.util.TickThread;

@FunctionalInterface
public interface TickThreadConstructor<T extends TickThread, S> {
    T construct(Runnable runnable, String name, S self);
}
