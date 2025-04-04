package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkUpgradeGenericStatusTask;
import java.lang.invoke.VarHandle;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkSystemTaskQueue implements PrioritisedExecutor {

    private final AtomicLong taskIdGenerator = new AtomicLong();
    private final AtomicLong scheduledTasks = new AtomicLong();
    private final AtomicLong executedTasks = new AtomicLong();
    private final AtomicLong subOrderGenerator = new AtomicLong();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final ConcurrentSkipListMap<ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder, Boolean> tasks = new ConcurrentSkipListMap<>(ChunkSystemTaskQueue.PrioritisedQueuedTask.COMPARATOR);
    private final TheChunkSystem chunkSystem;

    public ChunkSystemTaskQueue(TheChunkSystem chunkSystem) {
        this.chunkSystem = chunkSystem;
    }

    @Override
    public long getTotalTasksScheduled() {
        return this.scheduledTasks.get();
    }

    @Override
    public long getTotalTasksExecuted() {
        return this.executedTasks.get();
    }

    @Override
    public long generateNextSubOrder() {
        return this.subOrderGenerator.getAndIncrement();
    }

    @Override
    public boolean shutdown() {
        return !this.shutdown.getAndSet(true);
    }

    @Override
    public boolean isShutdown() {
        return this.shutdown.get();
    }

    @Override
    public boolean executeTask() {
        for (; ; ) {
            final Map.Entry<ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder, Boolean> firstEntry = this.tasks.pollFirstEntry();
            if (firstEntry != null) {
                final ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder task = firstEntry.getKey();
                task.markRemoved();
                if (!task.task.execute()) {
                    continue;
                }
                return true;
            }

            return false;
        }
    }

    @Override
    public PrioritisedTask createTask(final Runnable task) {
        return this.createTask(task, Priority.NORMAL, this.generateNextSubOrder());
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority) {
        return this.createTask(task, priority, this.generateNextSubOrder());
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder) {
        return new ChunkSystemTaskQueue.PrioritisedQueuedTask(task, priority, subOrder);
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task) {
        return this.queueTask(task, Priority.NORMAL, this.generateNextSubOrder());
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task, final Priority priority) {
        return this.queueTask(task, priority, this.generateNextSubOrder());
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task, final Priority priority, final long subOrder) {
        final ChunkSystemTaskQueue.PrioritisedQueuedTask ret = new ChunkSystemTaskQueue.PrioritisedQueuedTask(task, priority, subOrder);

        ret.queue();

        return ret;
    }

    private final class PrioritisedQueuedTask implements PrioritisedExecutor.PrioritisedTask {
        public static final Comparator<ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder> COMPARATOR = (final ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder t1, final ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder t2) -> {
            final int priorityCompare = t1.priority - t2.priority;
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            final int subOrderCompare = Long.compare(t1.subOrder, t2.subOrder);
            if (subOrderCompare != 0) {
                return subOrderCompare;
            }

            return Long.compare(t1.id, t2.id);
        };
        private final long id;
        private final Runnable execute;
        private Priority priority;
        private long subOrder;
        private ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder holder;

        public PrioritisedQueuedTask(final Runnable execute, final Priority priority, final long subOrder) {
            if (!Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            this.execute = execute;
            this.priority = priority;
            this.subOrder = subOrder;
            this.id = ChunkSystemTaskQueue.this.taskIdGenerator.getAndIncrement();
        }

        @Override
        public PrioritisedExecutor getExecutor() {
            return ChunkSystemTaskQueue.this;
        }

        @Override
        public boolean queue() {
            synchronized (this) {
                if (this.holder != null || this.priority == Priority.COMPLETING) {
                    return false;
                }

                if (ChunkSystemTaskQueue.this.isShutdown()) {
                    throw new IllegalStateException("Queue is shutdown");
                }

                this.holder = new Holder(this, this.priority.priority, this.subOrder, this.id);

                ChunkSystemTaskQueue.this.scheduledTasks.getAndIncrement();
                // try and use our priority manager more, but we cannot always
                // use our own, as some chunk tasks are either not runnables, or
                // do not contain chunk positions
                int priority = this.priority.priority;
                Runnable instance = this.holder.task.execute;
                if (instance instanceof ChunkFullTask full) {
                    priority = full.world.chunkSystemPriorities.priority(full.chunkX, full.chunkZ);
                } else if (instance instanceof ChunkUpgradeGenericStatusTask gen) {
                    priority = gen.world.chunkSystemPriorities.priority(gen.chunkX, gen.chunkZ);
                }
                if (this.priority.isHigherOrEqualPriority(Priority.BLOCKING)) {
                    priority = PriorityHandler.BLOCKING;
                }
                ChunkSystemTaskQueue.this.chunkSystem.schedule(this.holder.task.execute, priority);
            }

            if (ChunkSystemTaskQueue.this.isShutdown()) {
                this.cancel();
                throw new IllegalStateException("Queue is shutdown");
            }


            return true;
        }

        @Override
        public boolean isQueued() {
            synchronized (this) {
                return this.holder != null && this.priority != Priority.COMPLETING;
            }
        }

        @Override
        public boolean cancel() {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING) {
                    return false;
                }

                this.priority = Priority.COMPLETING;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    ChunkSystemTaskQueue.this.executedTasks.getAndIncrement();
                }

                return true;
            }
        }

        @Override
        public boolean execute() {
            final boolean increaseExecuted;

            synchronized (this) {
                if (this.priority == Priority.COMPLETING) {
                    return false;
                }

                this.priority = Priority.COMPLETING;

                if (increaseExecuted = (this.holder != null)) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                }
            }

            try {
                this.execute.run();
                return true;
            } finally {
                if (increaseExecuted) {
                    ChunkSystemTaskQueue.this.executedTasks.getAndIncrement();
                }
            }
        }

        @Override
        public Priority getPriority() {
            synchronized (this) {
                return this.priority;
            }
        }

        @Override
        public boolean setPriority(final Priority priority) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.priority == priority) {
                    return false;
                }

                this.priority = priority;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public boolean raisePriority(final Priority priority) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.priority.isHigherOrEqualPriority(priority)) {
                    return false;
                }

                this.priority = priority;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public boolean lowerPriority(Priority priority) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.priority.isLowerOrEqualPriority(priority)) {
                    return false;
                }

                this.priority = priority;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public long getSubOrder() {
            synchronized (this) {
                return this.subOrder;
            }
        }

        @Override
        public boolean setSubOrder(final long subOrder) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.subOrder == subOrder) {
                    return false;
                }

                this.subOrder = subOrder;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public boolean raiseSubOrder(long subOrder) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.subOrder >= subOrder) {
                    return false;
                }

                this.subOrder = subOrder;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public boolean lowerSubOrder(final long subOrder) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || this.subOrder <= subOrder) {
                    return false;
                }

                this.subOrder = subOrder;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        @Override
        public boolean setPriorityAndSubOrder(final Priority priority, final long subOrder) {
            synchronized (this) {
                if (this.priority == Priority.COMPLETING || (this.priority == priority && this.subOrder == subOrder)) {
                    return false;
                }

                this.priority = priority;
                this.subOrder = subOrder;

                if (this.holder != null) {
                    if (this.holder.markRemoved()) {
                        ChunkSystemTaskQueue.this.tasks.remove(this.holder);
                    }
                    this.holder = new ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder(this, priority.priority, this.subOrder, this.id);
                    ChunkSystemTaskQueue.this.tasks.put(this.holder, Boolean.TRUE);
                }

                return true;
            }
        }

        private static final class Holder {
            private static final VarHandle REMOVED_HANDLE = ConcurrentUtil.getVarHandle(ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder.class, "removed", boolean.class);
            private final ChunkSystemTaskQueue.PrioritisedQueuedTask task;
            private final int priority;
            private final long subOrder;
            private final long id;
            private volatile boolean removed;

            private Holder(final ChunkSystemTaskQueue.PrioritisedQueuedTask task, final int priority, final long subOrder,
                           final long id) {
                this.task = task;
                this.priority = priority;
                this.subOrder = subOrder;
                this.id = id;
            }

            public boolean markRemoved() {
                return !(boolean) REMOVED_HANDLE.getAndSet((ChunkSystemTaskQueue.PrioritisedQueuedTask.Holder) this, (boolean) true);
            }
        }
    }

}
