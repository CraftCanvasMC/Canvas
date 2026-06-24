package io.canvasmc.canvas.threadedregions;

import io.canvasmc.canvas.region.RegionThreadingTickManager;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import java.util.Objects;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;

// this class is literally just an abstraction layer between the Bukkit API and Canvas internals
@ApiStatus.Internal
public class ServerRegionTickManager implements RegionThreadingTickManager {

    public static final ServerRegionTickManager API_INSTANCE = new ServerRegionTickManager();

    public static void ensureScheduleHandle(TickRegionScheduler.RegionScheduleHandle handle, String reason) {
        if (handle != TickRegionScheduler.getCurrentTickingTask()) {
            throw new IllegalStateException(reason);
        }
    }

    @Override
    public float getTickRate() {
        return TickRegionScheduler.getTickRate();
    }

    @Override
    public void setTickRate(final float newRate) {
        TickRegionScheduler.setTickRate(newRate);
    }

    @Override
    public @NonNull RegionHandle getHandleAt(final @NonNull Chunk chunk) {
        return new ServerRegionHandle(
            Objects.requireNonNull(((CraftWorld) chunk.getWorld()).getHandle().regioniser.getRegionAtUnsynchronised(chunk.getX(), chunk.getZ()), "Region not present at location")
                .getData()
                .getRegionSchedulingHandle()
                .getTickManager()
        );
    }

    @Override
    public @NonNull RegionHandle getGlobalRegionHandle() {
        return new ServerRegionHandle(RegionizedServer.getGlobalTickData().getTickManager());
    }

    @Override
    public void sendUpdateToAllPlayers() {
        ScheduledHandleTickState.sendStateToAllPlayers();
    }

    public static class ServerRegionHandle implements RegionHandle {
        private final ScheduledHandleTickState state;

        public ServerRegionHandle(ScheduledHandleTickState state) {
            this.state = state;
        }

        @Override
        public void pause() {
            state.postAction(new ScheduledHandleTickState.Action.Pause());
        }

        @Override
        public void play() {
            state.postAction(new ScheduledHandleTickState.Action.Play());
        }

        @Override
        public void walk() {
            state.postAction(new ScheduledHandleTickState.Action.StopSprinting());
        }

        @Override
        public void sprint(int ticks) {
            state.postAction(new ScheduledHandleTickState.Action.StartSprinting(ticks));
        }

        @Override
        public boolean doesRunGameElements() {
            ensureScheduleHandle(state.scheduleHandle, "Can only check if running game elements on owning schedule handle");
            return state.doesRunGameElements();
        }

        @Override
        public boolean isSprinting() {
            ensureScheduleHandle(state.scheduleHandle, "Can only check if actively sprinting on owning schedule handle");
            return state.isSprinting();
        }
    }
}
