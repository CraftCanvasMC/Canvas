package io.canvasmc.canvas.server.level;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.AverageTickTimeAccessor;
import io.canvasmc.canvas.server.ThreadedServer;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.World;

public abstract class MinecraftServerWorld extends AbstractTickLoop<LevelThread, ServerLevel> implements TickRateManagerInstance, LevelAccess, AverageTickTimeAccessor {
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPost = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<Runnable> queuedForNextTickPre = new ConcurrentLinkedQueue<>();
    protected final ServerTickRateManager tickRateManager;

    public MinecraftServerWorld(final String name, final String debugName) {
        super(name, debugName, (r, n, self) -> new LevelThread(ThreadedServer.SERVER_THREAD_GROUP, r, n, self));
        this.tickRateManager = new ServerTickRateManager(this);
        this.setThreadModifier((levelThread) -> {
            levelThread.setName(this.name());
            levelThread.setPriority(Config.INSTANCE.tickLoopThreadPriority);
            levelThread.setDaemon(Config.INSTANCE.setDaemonForTickLoops);
            levelThread.setUncaughtExceptionHandler((_, throwable) -> LOGGER.error("Uncaught exception in level thread, {}", this.name(), throwable));
        });
        this.setPreBlockStart(() -> {
            if (Config.INSTANCE.useLevelThreadsAsChunkSourceMain) level().chunkSource.mainThread = this.owner;
        });
    }

    @Override
    public boolean pollInternal() {
        if (super.pollInternal()) {
            return true;
        } else {
            boolean ret = false;
            if (tickRateManager().isSprinting() || this.haveTime()) {
                ServerLevel worldserver = level();

                if (worldserver.getChunkSource().pollTask()) {
                    ret = true;
                }
            }

            return ret;
        }
    }

    public ServerLevel level() {
        return (ServerLevel) this;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public World getWorld() {
        return this.level().getWorld();
    }

    @Override
    public void scheduleOnThread(final Runnable runnable) {
        this.scheduleOnMain(runnable);
    }

    @Override
    public boolean isTicking() {
        return this.ticking;
    }

    @Override
    public CommandSourceStack createCommandSourceStack() {
        return MinecraftServer.getServer().createCommandSourceStack();
    }

    @Override
    public void onTickRateChanged() {
        MinecraftServer.getServer().onTickRateChanged();
    }

    @Override
    public void broadcastPacketsToPlayers(final Packet<?> packet) {
        for (final ServerPlayer player : this.level().players()) {
            player.connection.send(packet);
        }
    }

    @Override
    public void skipTickWait() {
        this.delayedTasksMaxNextTickTimeNanos = Util.getNanos();
        this.nextTickTimeNanos = Util.getNanos();
    }

    public boolean isLevelThread() {
        return Thread.currentThread() instanceof LevelThread;
    }

    @Override
    public void scheduleForPostNextTick(Runnable run) {
        queuedForNextTickPost.add(run);
    }

    @Override
    public void scheduleForPreNextTick(Runnable run) {
        queuedForNextTickPre.add(run);
    }

    @Override
    public double getAverageTickTime() {
        return getNanoSecondsFromLastTick() / 1_000_000;
    }
}
