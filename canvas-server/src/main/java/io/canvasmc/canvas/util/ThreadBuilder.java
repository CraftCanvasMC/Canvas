package io.canvasmc.canvas.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public interface ThreadBuilder extends Consumer<Thread> {
    AtomicInteger id = new AtomicInteger();

    default int getAndIncrementId() {
        return id.getAndIncrement();
    }
}
