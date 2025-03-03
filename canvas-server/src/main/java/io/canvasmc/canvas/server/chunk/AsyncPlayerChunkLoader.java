package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.server.AbstractTickLoop;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.function.BooleanSupplier;

public class AsyncPlayerChunkLoader extends AbstractTickLoop<TickThread, AsyncPlayerChunkLoader> {
    public static AsyncPlayerChunkLoader INSTANCE;

    public AsyncPlayerChunkLoader(final String name, final String debugName) {
        super(name, debugName);
        this.setThreadModifier((tickThread) -> {
            tickThread.setName(this.name());
            tickThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            tickThread.setUncaughtExceptionHandler((_, exception) -> LOGGER.error("Uncaught exception in player join thread", exception));
        });
        INSTANCE = this;
    }

    @Override
    public ServerTickRateManager tickRateManager() {
        return MinecraftServer.getServer().tickRateManager();
    }

    public void tick(BooleanSupplier hasTimeLeft) {
        ProfilerFiller profilerFiller = Profiler.get();
        if (MinecraftServer.getThreadedServer().hasStarted()) {
            for (ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                ServerChunkCache chunkSource = level.getChunkSource();
                if (level.tickRateManager().runsNormally() || level.spigotConfig.unloadFrozenChunks) {
                    if (chunkSource.ticksSinceLastPurgeStaleTicketsCall++ > Config.INSTANCE.ticksBetweenPurgeStaleTickets) {
                        chunkSource.distanceManager.purgeStaleTickets();
                        chunkSource.ticksSinceLastPurgeStaleTicketsCall = 0;
                    }
                }
                level.moonrise$getPlayerChunkLoader().tick();
                chunkSource.broadcastChangedChunks(profilerFiller);
                chunkSource.runDistanceManagerUpdates();
                chunkSource.chunkMap.tick(hasTimeLeft, true);
            }
        } else {
            MinecraftServer.getServer().getAllLevels().forEach((level -> {
                level.moonrise$getPlayerChunkLoader().tick();
                level.getChunkSource().pollTask();
            }));
        }
    }
}
