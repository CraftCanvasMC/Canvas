package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import java.lang.invoke.VarHandle;
import net.minecraft.server.level.ServerLevel;

public class ChunkRunnable implements Runnable {
    public final int chunkX;
    public final int chunkZ;
    public final ServerLevel world;
    private volatile Runnable toRun;
    private static final VarHandle TO_RUN_HANDLE = ConcurrentUtil.getVarHandle(ChunkRunnable.class, "toRun", Runnable.class);

    public ChunkRunnable(int chunkX, int chunkZ, ServerLevel world, Runnable run) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.world = world;
        this.toRun = run;
    }

    public void setRunnable(final Runnable run) {
        final Runnable prev = (Runnable)TO_RUN_HANDLE.compareAndExchange(this, (Runnable)null, run);
        if (prev != null) {
            throw new IllegalStateException("Runnable already set");
        }
    }

    @Override
    public void run() {
        ((Runnable)TO_RUN_HANDLE.getVolatile(this)).run();
    }
}
