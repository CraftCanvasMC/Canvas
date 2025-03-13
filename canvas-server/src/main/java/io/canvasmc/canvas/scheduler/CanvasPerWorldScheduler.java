package io.canvasmc.canvas.scheduler;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Validate;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class CanvasPerWorldScheduler implements RegionScheduler {
    private static final Map<ServerLevel, Long2ObjectOpenHashMap<List<CanvasPerWorldScheduler.GlobalScheduledTask>>> tasksByDeadline = new ConcurrentHashMap<>();
    private final Object stateLock = new Object();

    public CanvasPerWorldScheduler() {
    }

    public void tick(ServerLevel level) {
        final List<CanvasPerWorldScheduler.GlobalScheduledTask> run;
        Long2ObjectOpenHashMap<List<CanvasPerWorldScheduler.GlobalScheduledTask>> tasks = tasksByDeadline.computeIfAbsent(level, (_) -> new Long2ObjectOpenHashMap<>());
        synchronized (this.stateLock) {
            if (tasks.isEmpty()) {
                run = null;
            } else {
                run = tasks.remove(level.tickCount);
            }
        }

        if (run == null) {
            return;
        }

        for (GlobalScheduledTask globalScheduledTask : run) {
            globalScheduledTask.run();
        }
    }

    private void scheduleInternal(final CanvasPerWorldScheduler.GlobalScheduledTask task, final long delay) {
        // note: delay > 0
        synchronized (this.stateLock) {
            ServerLevel level = ((CraftWorld) task.world).getHandle();
            tasksByDeadline.computeIfAbsent(level, (_) -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(level.tickCount + delay, (final long keyInMap) -> {
                return new ArrayList<>();
            }).add(task);
        }
    }

    @Override
    public void execute(@NotNull final Plugin plugin, @NotNull final World world, final int chunkX, final int chunkZ, @NotNull final Runnable run) {
        Validate.notNull(plugin, "Plugin may not be null");
        Validate.notNull(run, "Runnable may not be null");

        this.run(plugin, world, chunkX, chunkZ, (final ScheduledTask task) -> {
            run.run();
        });
    }

    @Override
    public @NotNull ScheduledTask run(@NotNull final Plugin plugin, @NotNull final World world, final int chunkX, final int chunkZ, @NotNull final Consumer<ScheduledTask> task) {
        return this.runDelayed(plugin, world, chunkX, chunkZ, task, 1);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull final Plugin plugin, @NotNull final World world, final int chunkX, final int chunkZ, @NotNull final Consumer<ScheduledTask> task, final long delayTicks) {
        Validate.notNull(plugin, "Plugin may not be null");
        Validate.notNull(task, "Task may not be null");
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("Delay ticks may not be <= 0");
        }

        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }

        final CanvasPerWorldScheduler.GlobalScheduledTask ret = new CanvasPerWorldScheduler.GlobalScheduledTask(plugin, world, -1, task);

        this.scheduleInternal(ret, delayTicks);

        if (!plugin.isEnabled()) {
            // handle race condition where plugin is disabled asynchronously
            ret.cancel();
        }

        return ret;
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull final Plugin plugin, @NotNull final World world, final int chunkX, final int chunkZ, @NotNull final Consumer<ScheduledTask> task, final long initialDelayTicks, final long periodTicks) {
        Validate.notNull(plugin, "Plugin may not be null");
        Validate.notNull(task, "Task may not be null");
        if (initialDelayTicks <= 0) {
            throw new IllegalArgumentException("Initial delay ticks may not be <= 0");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("Period ticks may not be <= 0");
        }

        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }

        final CanvasPerWorldScheduler.GlobalScheduledTask ret = new CanvasPerWorldScheduler.GlobalScheduledTask(plugin, world, periodTicks, task);

        this.scheduleInternal(ret, initialDelayTicks);

        if (!plugin.isEnabled()) {
            // handle race condition where plugin is disabled asynchronously
            ret.cancel();
        }

        return ret;
    }

    private final class GlobalScheduledTask implements ScheduledTask, Runnable {

        private static final int STATE_IDLE = 0;
        private static final int STATE_EXECUTING = 1;
        private static final int STATE_EXECUTING_CANCELLED = 2;
        private static final int STATE_FINISHED = 3;
        private static final int STATE_CANCELLED = 4;
        private static final VarHandle STATE_HANDLE = ConcurrentUtil.getVarHandle(CanvasPerWorldScheduler.GlobalScheduledTask.class, "state", int.class);
        private final Plugin plugin;
        private final World world;
        private final long repeatDelay; // in ticks
        private Consumer<ScheduledTask> run;
        private volatile int state;

        private GlobalScheduledTask(final Plugin plugin, World world, final long repeatDelay, final Consumer<ScheduledTask> run) {
            this.plugin = plugin;
            this.world = world;
            this.repeatDelay = repeatDelay;
            this.run = run;
        }

        private int getStateVolatile() {
            return (int) STATE_HANDLE.get(this);
        }

        private void setStateVolatile(final int value) {
            STATE_HANDLE.setVolatile(this, value);
        }

        private int compareAndExchangeStateVolatile(final int expect, final int update) {
            return (int) STATE_HANDLE.compareAndExchange(this, expect, update);
        }

        @Override
        public void run() {
            final boolean repeating = this.isRepeatingTask();
            if (STATE_IDLE != this.compareAndExchangeStateVolatile(STATE_IDLE, STATE_EXECUTING)) {
                // cancelled
                return;
            }

            try {
                this.run.accept(this);
            } catch (final Throwable throwable) {
                this.plugin.getLogger().log(Level.WARNING, "Global task for " + this.plugin.getDescription().getFullName() + " generated an exception", throwable);
            } finally {
                boolean reschedule = false;
                if (!repeating) {
                    this.setStateVolatile(STATE_FINISHED);
                } else if (STATE_EXECUTING == this.compareAndExchangeStateVolatile(STATE_EXECUTING, STATE_IDLE)) {
                    reschedule = true;
                } // else: cancelled repeating task

                if (!reschedule) {
                    this.run = null;
                } else {
                    CanvasPerWorldScheduler.this.scheduleInternal(this, this.repeatDelay);
                }
            }
        }

        @Override
        public Plugin getOwningPlugin() {
            return this.plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return this.repeatDelay > 0;
        }

        @Override
        public CancelledState cancel() {
            for (int curr = this.getStateVolatile(); ; ) {
                switch (curr) {
                    case STATE_IDLE: {
                        if (STATE_IDLE == (curr = this.compareAndExchangeStateVolatile(STATE_IDLE, STATE_CANCELLED))) {
                            this.state = STATE_CANCELLED;
                            this.run = null;
                            return CancelledState.CANCELLED_BY_CALLER;
                        }
                        // try again
                        continue;
                    }
                    case STATE_EXECUTING: {
                        if (!this.isRepeatingTask()) {
                            return CancelledState.RUNNING;
                        }
                        if (STATE_EXECUTING == (curr = this.compareAndExchangeStateVolatile(STATE_EXECUTING, STATE_EXECUTING_CANCELLED))) {
                            return CancelledState.NEXT_RUNS_CANCELLED;
                        }
                        // try again
                        continue;
                    }
                    case STATE_EXECUTING_CANCELLED: {
                        return CancelledState.NEXT_RUNS_CANCELLED_ALREADY;
                    }
                    case STATE_FINISHED: {
                        return CancelledState.ALREADY_EXECUTED;
                    }
                    case STATE_CANCELLED: {
                        return CancelledState.CANCELLED_ALREADY;
                    }
                    default: {
                        throw new IllegalStateException("Unknown state: " + curr);
                    }
                }
            }
        }

        @Override
        public ExecutionState getExecutionState() {
            final int state = this.getStateVolatile();
            switch (state) {
                case STATE_IDLE:
                    return ExecutionState.IDLE;
                case STATE_EXECUTING:
                    return ExecutionState.RUNNING;
                case STATE_EXECUTING_CANCELLED:
                    return ExecutionState.CANCELLED_RUNNING;
                case STATE_FINISHED:
                    return ExecutionState.FINISHED;
                case STATE_CANCELLED:
                    return ExecutionState.CANCELLED;
                default: {
                    throw new IllegalStateException("Unknown state: " + state);
                }
            }
        }

        public World getWorld() {
            return world;
        }
    }
}
