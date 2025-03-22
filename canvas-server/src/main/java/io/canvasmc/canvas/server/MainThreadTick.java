package io.canvasmc.canvas.server;

import java.util.function.BooleanSupplier;

public class MainThreadTick extends AbstractTickLoop {
    private final ThreadedServer threadedServer;

    public MainThreadTick(final String formalName, final String debugName, final ThreadedServer threadedServer) {
        super(formalName, debugName);
        this.threadedServer = threadedServer;
    }

    @Override
    protected void blockTick(final BooleanSupplier hasTimeLeft, final int tickCount) {
        threadedServer.tickSection = this.server.tick(tickSection);
    }

    @Override
    public boolean shouldSleep() {
        // the main thread should NEVER sleep
        return false;
    }
}
