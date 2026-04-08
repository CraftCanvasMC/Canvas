package io.canvasmc.canvas.tick;

import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

// Note: if anything is Long.MIN_VALUE, it isn't set
public final class ScheduledHandleTickState {
    public static final long UNSET = Long.MIN_VALUE;

    // this ensures we process actions in a synchronized manner that means we can
    // ensure the state stays consistent throughout the tick
    private final ConcurrentLinkedQueue<Action> actionQueue = new ConcurrentLinkedQueue<>();
    final TickRegionScheduler.RegionScheduleHandle scheduleHandle;

    private long tickCountToSprintTo = UNSET;
    private long startSprintNanos = UNSET;
    private long lastTickTimeEndNanos = UNSET;

    private boolean runsGameElements = true;

    public ScheduledHandleTickState(TickRegionScheduler.RegionScheduleHandle scheduleHandle) {
        this.scheduleHandle = scheduleHandle;
    }

    public static void sendStateToAllPlayers() {
        for (final ServerPlayer player : MinecraftServer.getServer().getPlayerList().players) {
            player.scheduleToOrRun(() -> {
                player.level().getCurrentWorldData().regionData.getRegionSchedulingHandle().getTickManager().sendStateToPlayer(player);
            });
        }
    }

    public boolean doesRunGameElements() {
        return runsGameElements;
    }

    public boolean isEntityFrozen(Entity entity) {
        return !doesRunGameElements() && !(entity instanceof Player) && entity.countPlayerPassengers() <= 0;
    }

    public boolean isSprinting() {
        return tickCountToSprintTo != UNSET;
    }

    public long tickStart(long nanos) {
        // process all actions first
        processAllActions();

        // check if overloaded
        if (lastTickTimeEndNanos != UNSET && lastTickTimeEndNanos + TickRegionScheduler.getTimeBetweenTicks() + (io.canvasmc.canvas.Config.INSTANCE.scheduler.overloadedLogMillis * 1_000_000L) <= nanos) {
            // missed deadline to be considered "overloaded"
            double seconds = (nanos - (lastTickTimeEndNanos + TickRegionScheduler.getTimeBetweenTicks())) / 1_000_000_000.0;
            String formatted = String.format("%.2f", seconds);
            if (scheduleHandle instanceof RegionizedServer.GlobalTickTickHandle) {
                TickRegionScheduler.LOGGER.warn("Global tick missed deadline by {}s! Is the scheduler overloaded?", formatted);
            }
            else if (scheduleHandle instanceof TickRegions.ConcreteRegionTickHandle regionTickHandle)
                TickRegionScheduler.LOGGER.warn("Region surrounding {} in world '{}' missed deadline by {}s! Is the scheduler overloaded?",
                    regionTickHandle.region.region.getCenterChunk(), regionTickHandle.region.world.dimension().identifier().getPath(), formatted);
        }

        return tickSprint();
    }

    public void postAction(@NonNull Action action) {
        actionQueue.add(action);
    }

    public void tickEnd(long nanos) {
        lastTickTimeEndNanos = nanos;
    }

    public void sendStateToPlayer(@NonNull ServerPlayer player) {
        player.connection.send(new ClientboundTickingStatePacket(TickRegionScheduler.getTickRate(), !runsGameElements));
    }

    private void processAllActions() {
        boolean processed = false;
        Action action;
        while ((action = actionQueue.poll()) != null) {
            action.apply(this);
            processed = true;
        }
        // if we processed updates, send the new updates to the client
        if (processed) {
            sendStateToAllPlayers();
        }
    }

    private void pause() {
        runsGameElements = false;
    }

    private void play() {
        runsGameElements = true;
    }

    private void startSprinting(long howLongInTicks) {
        if (howLongInTicks <= 0) {
            // not executing any sprint time, expired
            return;
        }
        tickCountToSprintTo = scheduleHandle.getCurrentTick() + howLongInTicks;
        startSprintNanos = System.nanoTime();
    }

    private void forceStopSprinting() {
        if (tickCountToSprintTo == UNSET) return;
        // if we aren't currently sprinting, we set the target to the current tick
        // and then tick to clear the state
        tickCountToSprintTo = scheduleHandle.getCurrentTick();
        tickSprint();
    }

    private long tickSprint() {
        if (tickCountToSprintTo != UNSET) {
            if (scheduleHandle.getCurrentTick() >= tickCountToSprintTo) {
                // we passed the sprint deadline, clear sprint state
                tickCountToSprintTo = UNSET;
                TickRegionScheduler.LOGGER.info("Schedule handle {} finished tick sprint in {}ms",
                    getString(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startSprintNanos)
                );
                startSprintNanos = UNSET;
            }
            // we return 1L instead of 0L like Vanilla because this is used for division
            // later, and we can't divide by 0
            return 1L;
        }
        return TickRegionScheduler.getTimeBetweenTicks();
    }

    private @NonNull String getString() {
        return scheduleHandle instanceof RegionizedServer.GlobalTickTickHandle ? "Global tick" : "Region around " + scheduleHandle.region.region.getCenterChunk();
    }

    @Override
    public String toString() {
        return "ScheduledHandleTickState{" +
            "tickCountToSprintTo=" + tickCountToSprintTo +
            ", startSprintNanos=" + startSprintNanos +
            ", scheduleHandle=" + getString() +
            '}';
    }

    public interface Action {
        void apply(ScheduledHandleTickState state);

        record StartSprinting(long howLongInTicks) implements Action {
            @Override
            public void apply(final @NonNull ScheduledHandleTickState state) {
                state.startSprinting(howLongInTicks);
            }
        }

        class StopSprinting implements Action {
            @Override
            public void apply(final @NonNull ScheduledHandleTickState state) {
                state.forceStopSprinting();
            }
        }

        class Pause implements Action {
            @Override
            public void apply(final @NonNull ScheduledHandleTickState state) {
                state.pause();
            }
        }

        class Play implements Action {
            @Override
            public void apply(final @NonNull ScheduledHandleTickState state) {
                state.play();
            }
        }
    }
}
