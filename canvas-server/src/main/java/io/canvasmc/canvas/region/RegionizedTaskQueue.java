package io.canvasmc.canvas.region;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.lang.invoke.VarHandle;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;

public final class RegionizedTaskQueue {

    private static final TicketType<Unit> TASK_QUEUE_TICKET = TicketType.create("task_queue_ticket", (a, b) -> 0);

    public PrioritisedExecutor.PrioritisedTask createChunkTask(final ServerLevel world, final int chunkX, final int chunkZ,
                                                               final Runnable run) {
        return this.createChunkTask(world, chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask createChunkTask(final ServerLevel world, final int chunkX, final int chunkZ,
                                                               final Runnable run, final Priority priority) {
        return new PrioritisedQueue.ChunkBasedPriorityTask(world.taskQueueRegionData, chunkX, chunkZ, true, run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask createTickTaskQueue(final ServerLevel world, final int chunkX, final int chunkZ,
                                                                   final Runnable run) {
        return this.createTickTaskQueue(world, chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask createTickTaskQueue(final ServerLevel world, final int chunkX, final int chunkZ,
                                                                   final Runnable run, final Priority priority) {
        return new PrioritisedQueue.ChunkBasedPriorityTask(world.taskQueueRegionData, chunkX, chunkZ, false, run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask queueChunkTask(final ServerLevel world, final int chunkX, final int chunkZ,
                                                              final Runnable run) {
        return this.queueChunkTask(world, chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask queueChunkTask(final ServerLevel world, final int chunkX, final int chunkZ,
                                                              final Runnable run, final Priority priority) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createChunkTask(world, chunkX, chunkZ, run, priority);

        ret.queue();

        return ret;
    }

    public PrioritisedExecutor.PrioritisedTask queueTickTaskQueue(final ServerLevel world, final int chunkX, final int chunkZ,
                                                                  final Runnable run) {
        return this.queueTickTaskQueue(world, chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask queueTickTaskQueue(final ServerLevel world, final int chunkX, final int chunkZ,
                                                                  final Runnable run, final Priority priority) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTickTaskQueue(world, chunkX, chunkZ, run, priority);

        ret.queue();

        return ret;
    }

    public static final class WorldRegionTaskData {
        private final ServerLevel world;
        public final MultiThreadedQueue<Runnable> globalChunkTask = new MultiThreadedQueue<>();
        private final ConcurrentLong2ReferenceChainedHashTable<ReferenceCountData> referenceCounters = new ConcurrentLong2ReferenceChainedHashTable<>();

        public WorldRegionTaskData(final ServerLevel world) {
            this.world = world;
        }

        private boolean executeGlobalChunkTask() {
            final Runnable run = this.globalChunkTask.poll();
            if (run != null) {
                run.run();
                return true;
            }
            return false;
        }

        public void drainGlobalChunkTasks() {
            while (this.executeGlobalChunkTask());
        }

        public void pushGlobalChunkTask(final Runnable run) {
            this.globalChunkTask.add(run);
        }

        private PrioritisedQueue getQueue(final boolean synchronise, final int chunkX, final int chunkZ, final boolean isChunkTask) {
            final ThreadedRegionizer<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> regioniser = this.world.regioniser;
            final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region
                = synchronise ? regioniser.getRegionAtUnsynchronised(chunkX, chunkZ) : regioniser.getRegionAtUnsynchronised(chunkX, chunkZ);
            if (region == null) {
                return null;
            }
            final RegionTaskQueueData taskQueueData = region.getData().tickData.getTaskQueueData();
            return (isChunkTask ? taskQueueData.chunkQueue : taskQueueData.tickTaskQueue);
        }

        private void removeTicket(final long coord) {
            this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.removeTicketAtLevel(
                TASK_QUEUE_TICKET, coord, ChunkHolderManager.MAX_TICKET_LEVEL, Unit.INSTANCE
            );
        }

        private void addTicket(final long coord) {
            this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAtLevel(
                TASK_QUEUE_TICKET, coord, ChunkHolderManager.MAX_TICKET_LEVEL, Unit.INSTANCE
            );
        }

        private void processTicketUpdates(final long coord) {
            this.world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates(CoordinateUtils.getChunkX(coord), CoordinateUtils.getChunkZ(coord));
        }

        // note: only call on acquired referenceCountData
        private void ensureTicketAdded(final long coord, final ReferenceCountData referenceCountData) {
            if (!referenceCountData.addedTicket) {
                // fine if multiple threads do this, no removeTicket may be called for this coord due to reference count inc
                this.addTicket(coord);
                referenceCountData.addedTicket = true;
            }
        }

        private void decrementReference(final ReferenceCountData referenceCountData, final long coord) {
            if (!referenceCountData.decreaseReferenceCount()) {
                return;
            } // else: need to remove ticket

            // note: it is possible that another thread increments and then removes the reference before we can, so
            //       use ifPresent
            this.referenceCounters.computeIfPresent(coord, (final long keyInMap, final ReferenceCountData valueInMap) -> {
                if (valueInMap.referenceCount.get() != 0L) {
                    return valueInMap;
                }

                // note: valueInMap may not be referenceCountData

                // possible to invoke this outside of the compute call, but not required and requires additional logic
                WorldRegionTaskData.this.removeTicket(keyInMap);

                return null;
            });
        }

        private ReferenceCountData incrementReference(final long coord) {
            ReferenceCountData referenceCountData = this.referenceCounters.get(coord);

            if (referenceCountData != null && referenceCountData.addCount()) {
                this.ensureTicketAdded(coord, referenceCountData);
                return referenceCountData;
            }

            referenceCountData = this.referenceCounters.compute(coord, (final long keyInMap, final ReferenceCountData valueInMap) -> {
                if (valueInMap == null) {
                    // sets reference count to 1
                    return new ReferenceCountData();
                }
                // OK if we add from 0, the remove call will use compute() and catch this race condition
                valueInMap.referenceCount.getAndIncrement();

                return valueInMap;
            });

            this.ensureTicketAdded(coord, referenceCountData);

            return referenceCountData;
        }
    }

    private static final class ReferenceCountData {

        public final AtomicLong referenceCount = new AtomicLong(1L);
        public volatile boolean addedTicket;

        // returns false if reference count is 0, otherwise increments ref count
        public boolean addCount() {
            int failures = 0;
            for (long curr = this.referenceCount.get();;) {
                for (int i = 0; i < failures; ++i) {
                    Thread.onSpinWait();
                }

                if (curr == 0L) {
                    return false;
                }

                if (curr == (curr = this.referenceCount.compareAndExchange(curr, curr + 1L))) {
                    return true;
                }

                ++failures;
            }
        }

        // returns true if new reference count is 0
        public boolean decreaseReferenceCount() {
            final long res = this.referenceCount.decrementAndGet();
            if (res >= 0L) {
                return res == 0L;
            } else {
                throw new IllegalStateException("Negative reference count");
            }
        }
    }

    public static final class RegionTaskQueueData {
        private final PrioritisedQueue tickTaskQueue = new PrioritisedQueue();
        private final PrioritisedQueue chunkQueue = new PrioritisedQueue();
        private final WorldRegionTaskData worldRegionTaskData;

        public RegionTaskQueueData(final WorldRegionTaskData worldRegionTaskData) {
            this.worldRegionTaskData = worldRegionTaskData;
        }

        void mergeInto(final RegionTaskQueueData into) {
            this.tickTaskQueue.mergeInto(into.tickTaskQueue);
            this.chunkQueue.mergeInto(into.chunkQueue);
        }

        public boolean executeTickTask() {
            return this.tickTaskQueue.executeTask(null);
        }

        public boolean executeTickTask(Priority only) {
            return this.tickTaskQueue.executeTask(only);
        }

        public boolean executeChunkTask() {
            return this.chunkQueue.executeTask(null);
        }

        void split(final ThreadedRegionizer<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> regioniser,
                   final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> into) {
            this.tickTaskQueue.split(
                false, regioniser, into
            );
            this.chunkQueue.split(
                true, regioniser, into
            );
        }

        public void drainTasks() {
            final PrioritisedQueue tickTaskQueue = this.tickTaskQueue;
            final PrioritisedQueue chunkTaskQueue = this.chunkQueue;

            int allowedTickTasks = tickTaskQueue.getScheduledTasks();
            int allowedChunkTasks = chunkTaskQueue.getScheduledTasks();

            boolean executeTickTasks = allowedTickTasks > 0;
            boolean executeChunkTasks = allowedChunkTasks > 0;
            boolean executeGlobalTasks = true;

            do {
                executeTickTasks = executeTickTasks && allowedTickTasks-- > 0 && tickTaskQueue.executeTask(null);
                executeChunkTasks = executeChunkTasks && allowedChunkTasks-- > 0 && chunkTaskQueue.executeTask(null);
                executeGlobalTasks = executeGlobalTasks && this.worldRegionTaskData.executeGlobalChunkTask();
            } while (executeTickTasks | executeChunkTasks | executeGlobalTasks);

            if (allowedChunkTasks > 0) {
                // if we executed chunk tasks, we should try to process ticket updates for full status changes
                this.worldRegionTaskData.world.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();
            }
        }

        public int size() {
            return this.tickTaskQueue.getScheduledTasks() + this.chunkQueue.getScheduledTasks();
        }

        public boolean hasTasks() {
            return !this.tickTaskQueue.isEmpty() || !this.chunkQueue.isEmpty();
        }

        public boolean hasChunkTasks() {
            return !this.chunkQueue.isEmpty();
        }
    }

    static final class PrioritisedQueue {
        private final ArrayDeque<ChunkBasedPriorityTask>[] queues = new ArrayDeque[Priority.TOTAL_SCHEDULABLE_PRIORITIES]; {
            for (int i = 0; i < Priority.TOTAL_SCHEDULABLE_PRIORITIES; ++i) {
                this.queues[i] = new ArrayDeque<>();
            }
        }
        private boolean isDestroyed;

        public int getScheduledTasks() {
            synchronized (this) {
                int ret = 0;

                for (final ArrayDeque<ChunkBasedPriorityTask> queue : this.queues) {
                    ret += queue.size();
                }

                return ret;
            }
        }

        public boolean isEmpty() {
            final ArrayDeque<ChunkBasedPriorityTask>[] queues = this.queues;
            final int max = Priority.IDLE.priority;
            synchronized (this) {
                for (int i = 0; i <= max; ++i) {
                    if (!queues[i].isEmpty()) {
                        return false;
                    }
                }
                return true;
            }
        }

        public void mergeInto(final PrioritisedQueue target) {
            synchronized (this) {
                this.isDestroyed = true;
                mergeInto(target, this.queues);
            }
        }

        private static void mergeInto(final PrioritisedQueue target, final ArrayDeque<ChunkBasedPriorityTask>[] thisQueues) {
            synchronized (target) {
                final ArrayDeque<ChunkBasedPriorityTask>[] otherQueues = target.queues;
                for (int i = 0; i < thisQueues.length; ++i) {
                    final ArrayDeque<ChunkBasedPriorityTask> fromQ = thisQueues[i];
                    final ArrayDeque<ChunkBasedPriorityTask> intoQ = otherQueues[i];

                    // it is possible for another thread to queue tasks into the target queue before we do
                    // since only the ticking region can poll, we don't have to worry about it when they are being queued -
                    // but when we are merging, we need to ensure order is maintained (notwithstanding priority changes)
                    // we can ensure order is maintained by adding all of the tasks from the fromQ into the intoQ at the
                    // front of the queue, but we need to use descending iterator to ensure we do not reverse
                    // the order of elements from fromQ
                    for (final Iterator<ChunkBasedPriorityTask> iterator = fromQ.descendingIterator(); iterator.hasNext();) {
                        intoQ.addFirst(iterator.next());
                    }
                }
            }
        }

        // into is a map of section coordinate to region
        public void split(final boolean isChunkData,
                          final ThreadedRegionizer<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> regioniser,
                          final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>> into) {
            final Reference2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>, ArrayDeque<ChunkBasedPriorityTask>[]>
                split = new Reference2ReferenceOpenHashMap<>();
            final int shift = regioniser.sectionChunkShift;
            synchronized (this) {
                this.isDestroyed = true;
                // like mergeTarget, we need to be careful about insertion order so we can maintain order when splitting

                // first, build the targets
                final ArrayDeque<ChunkBasedPriorityTask>[] thisQueues = this.queues;
                for (int i = 0; i < thisQueues.length; ++i) {
                    final ArrayDeque<ChunkBasedPriorityTask> fromQ = thisQueues[i];

                    for (final ChunkBasedPriorityTask task : fromQ) {
                        final int sectionX = task.chunkX >> shift;
                        final int sectionZ = task.chunkZ >> shift;
                        final long sectionKey = CoordinateUtils.getChunkKey(sectionX, sectionZ);
                        final ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>
                            region = into.get(sectionKey);
                        if (region == null) {
                            task.world.world.moonrise$getChunkTaskScheduler().scheduleChunkTaskEventually(task.chunkX, task.chunkZ, () -> {
                                MinecraftServer.getThreadedServer().taskQueue.queueChunkTask(task.world.world, task.chunkX, task.chunkZ, task.run, task.priority);
                            });
                            continue;
                        }

                        split.computeIfAbsent(region, (keyInMap) -> {
                            final ArrayDeque<ChunkBasedPriorityTask>[] ret = new ArrayDeque[Priority.TOTAL_SCHEDULABLE_PRIORITIES];

                            for (int k = 0; k < ret.length; ++k) {
                                ret[k] = new ArrayDeque<>();
                            }

                            return ret;
                        })[i].add(task);
                    }
                }

                // merge the targets into their queues
                for (final Iterator<Reference2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>, ArrayDeque<ChunkBasedPriorityTask>[]>>
                     iterator = split.reference2ReferenceEntrySet().fastIterator();
                     iterator.hasNext();) {
                    final Reference2ReferenceMap.Entry<ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData>, ArrayDeque<ChunkBasedPriorityTask>[]>
                        entry = iterator.next();
                    final RegionTaskQueueData taskQueueData = entry.getKey().getData().tickData.getTaskQueueData();
                    mergeInto(isChunkData ? taskQueueData.chunkQueue : taskQueueData.tickTaskQueue, entry.getValue());
                }
            }
        }

        /**
         * returns null if the task cannot be scheduled, returns false if this task queue is dead, and returns true
         * if the task was added
         */
        private Boolean tryPush(final ChunkBasedPriorityTask task) {
            final ArrayDeque<ChunkBasedPriorityTask>[] queues = this.queues;
            synchronized (this) {
                final Priority priority = task.getPriority();
                if (priority == Priority.COMPLETING) {
                    return null;
                }
                if (this.isDestroyed) {
                    return Boolean.FALSE;
                }
                queues[priority.priority].addLast(task);
                return Boolean.TRUE;
            }
        }

        private boolean executeTask(Priority priority) {
            final ArrayDeque<ChunkBasedPriorityTask>[] queues = this.queues;
            final int max = Priority.IDLE.priority;
            ChunkBasedPriorityTask task = null;
            ReferenceCountData referenceCounter = null;
            synchronized (this) {
                if (this.isDestroyed) {
                    return false;
                }

                search_loop:
                for (int i = 0; i <= max; ++i) {
                    if (priority != null && priority.isLowerPriority(Priority.getPriority(i))) break search_loop; // break once we reach the limit
                    final ArrayDeque<ChunkBasedPriorityTask> queue = queues[i];
                    while ((task = queue.pollFirst()) != null) {
                        if ((referenceCounter = task.trySetCompleting(i)) != null) {
                            break search_loop;
                        }
                    }
                }
            }

            if (task == null) {
                return false;
            }

            try {
                task.executeInternal();
            } finally {
                task.world.decrementReference(referenceCounter, task.sectionLowerLeftCoord);
            }

            return true;
        }

        private static final class ChunkBasedPriorityTask implements PrioritisedExecutor.PrioritisedTask {

            private static final ReferenceCountData REFERENCE_COUNTER_NOT_SET = new ReferenceCountData();
            static {
                REFERENCE_COUNTER_NOT_SET.referenceCount.set((long)Integer.MIN_VALUE);
            }

            private final WorldRegionTaskData world;
            private final int chunkX;
            private final int chunkZ;
            private final long sectionLowerLeftCoord; // chunk coordinate
            private final boolean isChunkTask;

            private volatile ReferenceCountData referenceCounter;
            private static final VarHandle REFERENCE_COUNTER_HANDLE = ConcurrentUtil.getVarHandle(ChunkBasedPriorityTask.class, "referenceCounter", ReferenceCountData.class);
            private Runnable run;
            private volatile Priority priority;
            private static final VarHandle PRIORITY_HANDLE = ConcurrentUtil.getVarHandle(ChunkBasedPriorityTask.class, "priority", Priority.class);

            ChunkBasedPriorityTask(final WorldRegionTaskData world, final int chunkX, final int chunkZ, final boolean isChunkTask,
                                   final Runnable run, final Priority priority) {
                this.world = world;
                this.chunkX = chunkX;
                this.chunkZ = chunkZ;
                this.isChunkTask = isChunkTask;
                this.run = run;
                this.setReferenceCounterPlain(REFERENCE_COUNTER_NOT_SET);
                this.setPriorityPlain(priority);

                final int regionShift = world.world.regioniser.sectionChunkShift;
                final int regionMask = (1 << regionShift) - 1;

                this.sectionLowerLeftCoord = CoordinateUtils.getChunkKey(chunkX & ~regionMask, chunkZ & ~regionMask);
            }

            private Priority getPriorityVolatile() {
                return (Priority)PRIORITY_HANDLE.getVolatile(this);
            }

            private void setPriorityPlain(final Priority priority) {
                PRIORITY_HANDLE.set(this, priority);
            }

            private void setPriorityVolatile(final Priority priority) {
                PRIORITY_HANDLE.setVolatile(this, priority);
            }

            private Priority compareAndExchangePriority(final Priority expect, final Priority update) {
                return (Priority)PRIORITY_HANDLE.compareAndExchange(this, expect, update);
            }

            private void setReferenceCounterPlain(final ReferenceCountData value) {
                REFERENCE_COUNTER_HANDLE.set(this, value);
            }

            private ReferenceCountData getReferenceCounterVolatile() {
                return (ReferenceCountData)REFERENCE_COUNTER_HANDLE.get(this);
            }

            private ReferenceCountData compareAndExchangeReferenceCounter(final ReferenceCountData expect, final ReferenceCountData update) {
                return (ReferenceCountData)REFERENCE_COUNTER_HANDLE.compareAndExchange(this, expect, update);
            }

            private void executeInternal() {
                try {
                    this.run.run();
                } finally {
                    this.run = null;
                }
            }

            private void cancelInternal() {
                this.run = null;
            }

            private boolean tryComplete(final boolean cancel) {
                int failures = 0;
                for (ReferenceCountData curr = this.getReferenceCounterVolatile();;) {
                    if (curr == null) {
                        return false;
                    }

                    for (int i = 0; i < failures; ++i) {
                        ConcurrentUtil.backoff();
                    }

                    if (curr != (curr = this.compareAndExchangeReferenceCounter(curr, null))) {
                        ++failures;
                        continue;
                    }

                    // we have the reference count, we win no matter what.
                    this.setPriorityVolatile(Priority.COMPLETING);

                    try {
                        if (cancel) {
                            this.cancelInternal();
                        } else {
                            this.executeInternal();
                        }
                    } finally {
                        if (curr != REFERENCE_COUNTER_NOT_SET) {
                            this.world.decrementReference(curr, this.sectionLowerLeftCoord);
                        }
                    }

                    return true;
                }
            }

            @Override
            public PrioritisedExecutor getExecutor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isQueued() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean queue() {
                if (this.getReferenceCounterVolatile() != REFERENCE_COUNTER_NOT_SET) {
                    return false;
                }

                final ReferenceCountData referenceCounter = this.world.incrementReference(this.sectionLowerLeftCoord);
                if (this.compareAndExchangeReferenceCounter(REFERENCE_COUNTER_NOT_SET, referenceCounter) != REFERENCE_COUNTER_NOT_SET) {
                    // we don't expect race conditions here, so it is OK if we have to needlessly reference count
                    this.world.decrementReference(referenceCounter, this.sectionLowerLeftCoord);
                    return false;
                }

                boolean synchronise = false;
                for (;;) {
                    // we need to synchronise for repeated operations so that we guarantee that we do not retrieve
                    // the same queue again, as the region lock will be given to us only when the merge/split operation
                    // is done
                    final PrioritisedQueue queue = this.world.getQueue(synchronise, this.chunkX, this.chunkZ, this.isChunkTask);

                    if (queue == null) {
                        if (!synchronise) {
                            // may be incorrectly null when unsynchronised
                            synchronise = true;
                            continue;
                        }
                        // may have been cancelled before we got to the queue
                        if (this.getReferenceCounterVolatile() != null) {
                            return false;
                        }
                        // the task never could be polled from the queue, so we return false
                        // don't decrement reference count, as we were certainly cancelled by another thread, which
                        // will decrement the reference count
                        return false;
                    }

                    synchronise = true;

                    final Boolean res = queue.tryPush(this);
                    if (res == null) {
                        // we were cancelled
                        // don't decrement reference count, as we were certainly cancelled by another thread, which
                        // will decrement the reference count
                        return false;
                    }

                    if (!res.booleanValue()) {
                        // failed, try again
                        continue;
                    }

                    // successfully queued
                    return true;
                }
            }

            private ReferenceCountData trySetCompleting(final int minPriority) {
                // first, try to set priority to EXECUTING
                for (Priority curr = this.getPriorityVolatile();;) {
                    if (curr.isLowerPriority(minPriority)) {
                        return null;
                    }

                    if (curr == (curr = this.compareAndExchangePriority(curr, Priority.COMPLETING))) {
                        break;
                    } // else: continue
                }

                for (ReferenceCountData curr = this.getReferenceCounterVolatile();;) {
                    if (curr == null) {
                        // something acquired before us
                        return null;
                    }

                    if (curr == REFERENCE_COUNTER_NOT_SET) {
                        throw new IllegalStateException();
                    }

                    if (curr != (curr = this.compareAndExchangeReferenceCounter(curr, null))) {
                        continue;
                    }

                    return curr;
                }
            }

            private void updatePriorityInQueue() {
                boolean synchronise = false;
                for (;;) {
                    final ReferenceCountData referenceCount = this.getReferenceCounterVolatile();
                    if (referenceCount == REFERENCE_COUNTER_NOT_SET || referenceCount == null) {
                        // cancelled or not queued
                        return;
                    }

                    if (this.getPriorityVolatile() == Priority.COMPLETING) {
                        // cancelled
                        return;
                    }

                    // we need to synchronise for repeated operations so that we guarantee that we do not retrieve
                    // the same queue again, as the region lock will be given to us only when the merge/split operation
                    // is done
                    final PrioritisedQueue queue = this.world.getQueue(synchronise, this.chunkX, this.chunkZ, this.isChunkTask);

                    if (queue == null) {
                        if (!synchronise) {
                            // may be incorrectly null when unsynchronised
                            synchronise = true;
                            continue;
                        }
                        // must have been removed
                        return;
                    }

                    synchronise = true;

                    final Boolean res = queue.tryPush(this);
                    if (res == null) {
                        // we were cancelled
                        return;
                    }

                    if (!res.booleanValue()) {
                        // failed, try again
                        continue;
                    }

                    // successfully queued
                    return;
                }
            }

            @Override
            public Priority getPriority() {
                return this.getPriorityVolatile();
            }

            @Override
            public boolean lowerPriority(final Priority priority) {
                int failures = 0;
                for (Priority curr = this.getPriorityVolatile();;) {
                    if (curr == Priority.COMPLETING) {
                        return false;
                    }

                    if (curr.isLowerOrEqualPriority(priority)) {
                        return false;
                    }

                    for (int i = 0; i < failures; ++i) {
                        ConcurrentUtil.backoff();
                    }

                    if (curr == (curr = this.compareAndExchangePriority(curr, priority))) {
                        this.updatePriorityInQueue();
                        return true;
                    }
                    ++failures;
                }
            }

            @Override
            public long getSubOrder() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean setSubOrder(final long subOrder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean raiseSubOrder(final long subOrder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean lowerSubOrder(final long subOrder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean setPriorityAndSubOrder(final Priority priority, final long subOrder) {
                return this.setPriority(priority);
            }

            @Override
            public boolean setPriority(final Priority priority) {
                int failures = 0;
                for (Priority curr = this.getPriorityVolatile();;) {
                    if (curr == Priority.COMPLETING) {
                        return false;
                    }

                    if (curr == priority) {
                        return false;
                    }

                    for (int i = 0; i < failures; ++i) {
                        ConcurrentUtil.backoff();
                    }

                    if (curr == (curr = this.compareAndExchangePriority(curr, priority))) {
                        this.updatePriorityInQueue();
                        return true;
                    }
                    ++failures;
                }
            }

            @Override
            public boolean raisePriority(final Priority priority) {
                int failures = 0;
                for (Priority curr = this.getPriorityVolatile();;) {
                    if (curr == Priority.COMPLETING) {
                        return false;
                    }

                    if (curr.isHigherOrEqualPriority(priority)) {
                        return false;
                    }

                    for (int i = 0; i < failures; ++i) {
                        ConcurrentUtil.backoff();
                    }

                    if (curr == (curr = this.compareAndExchangePriority(curr, priority))) {
                        this.updatePriorityInQueue();
                        return true;
                    }
                    ++failures;
                }
            }

            @Override
            public boolean execute() {
                return this.tryComplete(false);
            }

            @Override
            public boolean cancel() {
                return this.tryComplete(true);
            }
        }
    }
}
