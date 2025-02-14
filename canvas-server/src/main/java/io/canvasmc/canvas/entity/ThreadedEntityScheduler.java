package io.canvasmc.canvas.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;

public class ThreadedEntityScheduler extends AbstractTickLoop<TickThread, ThreadedEntityScheduler> implements AverageTickTimeAccessor {

    public ThreadedEntityScheduler(final String name, final String debugName) {
        super(name, debugName);
        this.setThreadModifier((levelThread) -> {
            levelThread.setName(this.name());
            levelThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            levelThread.setDaemon(Config.INSTANCE.setDaemonForTickLoops);
            levelThread.setUncaughtExceptionHandler((_, throwable) -> LOGGER.error("Uncaught exception in entity thread", throwable));
        });
    }

    public void tickEntities() {
        for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
            level.tickEntities(true);
        }
    }

    @Override
    public double getAverageTickTime() {
        return getNanoSecondsFromLastTick() / 1_000_000;
    }

    @Override
    public String getName() {
        return name();
    }

    public ServerTickRateManager tickRateManager() {
        return MinecraftServer.getServer().tickRateManager();
    }
}
