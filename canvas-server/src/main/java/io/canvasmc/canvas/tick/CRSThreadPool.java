package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.set.LinkedSortedSet;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Scheduler thread pool implementation based off EDF scheduler in ConcurrentUtil
 * that supports intermediate task execution and task pinning
 */
@Deprecated(forRemoval = true)
public final class CRSThreadPool extends Scheduler {

    private static final Comparator<ScheduledState> TICK_COMPARATOR_BY_TIME = (final ScheduledState s1, final ScheduledState s2) -> {
        final SchedulableTick t1 = s1.tick;
        final SchedulableTick t2 = s2.tick;

        final int timeCompare = TimeUtil.compareTimes(t1.scheduledStart, t2.scheduledStart);
        if (timeCompare != 0) {
            return timeCompare;
        }

        return Long.signum(t1.id - t2.id);
    };
    private final LinkedSortedSet<ScheduledState> awaiting = new LinkedSortedSet<>(TICK_COMPARATOR_BY_TIME);
    private final StealingQueue queued = new StealingQueue(TICK_COMPARATOR_BY_TIME);
    private final Object scheduleLock = new Object();
    private final int threadCount;
    private TickThreadRunner[] runners;
    private Thread[] threads;
    private BitSet idleThreads;
    private volatile boolean halted;
    private volatile boolean supportsPinning = true;

    public final long runTaskBuff;
    public final long stealThresh;

    /**
     * Creates, but does not start, a scheduler thread pool with the specified number of threads
     * created using the specified thread factory.
     *
     * @param threads       Specified number of threads
     * @param threadFactory Specified thread factory
     * @see #start()
     */
    public CRSThreadPool(final int threads, final ThreadFactory threadFactory, long runTaskBuff, long stealThresh) {
        final BitSet idleThreads = new BitSet(threads);
        for (int i = 0; i < threads; ++i) {
            idleThreads.set(i);
        }
        this.idleThreads = idleThreads;

        final TickThreadRunner[] runners = new TickThreadRunner[threads];
        final Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; ++i) {
            runners[i] = new TickThreadRunner(i, this);
            t[i] = threadFactory.newThread(runners[i]);
        }

        this.threads = t;
        this.runners = runners;
        this.threadCount = threads;

        this.runTaskBuff = runTaskBuff;
        this.stealThresh = stealThresh;
    }

    /**
     * Starts the threads in this pool.
     */
    public void start() {
        for (final Thread thread : this.threads) {
            thread.start();
        }
    }

    @Override
    public void halt() {
        this.halted = true;
        for (final Thread thread : this.threads) {
            // force response to halt
            LockSupport.unpark(thread);
        }
    }

    @Override
    public boolean join(final long msToWait) {
        try {
            return this.join(msToWait, false);
        } catch (final InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean joinInterruptable(final long msToWait) throws InterruptedException {
        return this.join(msToWait, true);
    }

    private boolean join(final long msToWait, final boolean interruptable) throws InterruptedException {
        final long nsToWait = TimeUnit.MILLISECONDS.toNanos(msToWait);
        final long start = System.nanoTime();
        final long deadline = start + nsToWait;
        boolean interrupted = false;
        try {
            for (final Thread thread : this.threads) {
                while (thread.isAlive()) {
                    try {
                        if (msToWait > 0L) {
                            final long current = System.nanoTime();
                            if (current - deadline >= 0L) {
                                return false;
                            }
                            thread.join(Duration.ofNanos(deadline - current));
                        } else {
                            thread.join();
                        }
                    } catch (final InterruptedException ex) {
                        if (interruptable) {
                            throw ex;
                        }
                        interrupted = true;
                    }
                }
            }

            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public @NonNull TickThreadRunner getCurrentTickThreadRunner() {
        Thread curr = Thread.currentThread();
        for (final TickThreadRunner runner : this.runners) {
            if (runner.backingThread == curr) {
                return runner;
            }
        }
        throw new IllegalStateException("Couldn't locate current tick thread runner");
    }

    /**
     * Returns an array of the underlying scheduling threads.
     */
    public Thread[] getThreads() {
        return this.threads.clone();
    }

    @Override
    public Thread[] getCoreThreads() {
        return this.getThreads();
    }

    @Override
    public Thread @NonNull [] getAliveThreads() {
        final List<Thread> ret = new ArrayList<>(this.threads.length);
        for (final Thread thread : this.threads) {
            if (thread.isAlive()) {
                ret.add(thread);
            }
        }

        return ret.toArray(new Thread[0]);
    }

    private void insertFresh(final @NonNull ScheduledState task) {
        final TickThreadRunner[] runners = this.runners;

        if (task.isPinned()) {
            final int pinnedId = task.getPinnedThreadId();
            if (pinnedId >= 0 && pinnedId < runners.length) {
                final TickThreadRunner pinnedRunner = runners[pinnedId];

                if (this.idleThreads.get(pinnedId)) {
                    this.idleThreads.clear(pinnedId);
                    task.awaitingLink = this.awaiting.addLast(task);
                    pinnedRunner.acceptTask(task);
                    return;
                }

                final TickThreadRunner.TickThreadRunnerState pinnedState = pinnedRunner.state;
                if (pinnedState.state == TickThreadRunner.STATE_AWAITING_TICK) {
                    final ScheduledState currentTask = pinnedState.stateTarget;

                    if (TICK_COMPARATOR_BY_TIME.compare(task, currentTask) < 0) {
                        this.awaiting.remove(currentTask.awaitingLink);
                        currentTask.awaitingLink = null;
                        task.awaitingLink = this.awaiting.addLast(task);
                        // add with proper partition
                        if (currentTask.isPinned()) {
                            this.queued.add(currentTask, currentTask.getPinnedThreadId());
                        } else {
                            this.queued.add(currentTask, pinnedId);
                        }
                        pinnedRunner.replaceTask(task);
                        return;
                    }
                }

                // add to pinned thread partition
                this.queued.add(task, pinnedId);
                return;
            }
        }

        final int firstIdleThread = this.idleThreads.nextSetBit(0);

        if (firstIdleThread != -1) {
            // push to idle thread
            this.idleThreads.clear(firstIdleThread);
            final TickThreadRunner runner = runners[firstIdleThread];
            task.awaitingLink = this.awaiting.addLast(task);
            runner.acceptTask(task);
            return;
        }

        // try to replace the last awaiting task
        final ScheduledState last = this.awaiting.last();

        if (last != null && TICK_COMPARATOR_BY_TIME.compare(task, last) < 0) {
            // need to replace the last task
            this.awaiting.pollLast();
            last.awaitingLink = null;
            task.awaitingLink = this.awaiting.addLast(task);

            final TickThreadRunner runner = last.ownedBy;

            // add to proper partition
            if (last.isPinned()) {
                this.queued.add(last, last.getPinnedThreadId());
            } else {
                this.queued.add(last, runner.id);
            }

            runner.replaceTask(task);

            return;
        }

        // add to queue, will be picked up later
        this.queued.add(task);
    }

    private void takeTask(final TickThreadRunner runner, final @NonNull ScheduledState tick) {
        if (!this.awaiting.remove(tick.awaitingLink)) {
            throw new IllegalStateException("Task is not in awaiting");
        }
        tick.awaitingLink = null;
    }

    private ScheduledState returnTask(final TickThreadRunner runner, final ScheduledState reschedule) {
        if (reschedule != null) {
            this.queued.remove(reschedule);
            if (reschedule.isPinned()) {
                final int pinnedThreadId = reschedule.getPinnedThreadId();
                this.queued.add(reschedule, pinnedThreadId);

                // if task is now pinned to a *different* thread than the one that just ran it,
                // we need to wake up the new pinned thread if it's idle
                if (pinnedThreadId != runner.id && this.idleThreads.get(pinnedThreadId)) {
                    this.idleThreads.clear(pinnedThreadId);
                    final TickThreadRunner targetRunner = this.runners[pinnedThreadId];

                    // remove before handing off
                    this.queued.remove(reschedule);

                    // send task to idle runner
                    reschedule.awaitingLink = this.awaiting.addLast(reschedule);
                    targetRunner.acceptTask(reschedule);
                }
            } else {
                this.queued.add(reschedule, runner.id);
            }
        }

        ScheduledState ret = null;
        if (!this.queued.isEmpty()) {
            final boolean runnerIsPinned = runner.isPinnedTo();

            ret = this.queued.pollFor(runner.id, runnerIsPinned);;
        }

        if (ret == null) {
            this.idleThreads.set(runner.id);
        } else {
            ret.awaitingLink = this.awaiting.addLast(ret);
        }

        return ret;
    }

    @Override
    public void schedule(final SchedulableTick task) {
        synchronized (this.scheduleLock) {
            final ScheduledState state = new ScheduledState(task);
            if (!task.setState(state)) {
                throw new IllegalStateException("Task " + task + " is already scheduled or cancelled");
            }

            if (!state.tryMarkScheduled()) {
                throw new IllegalStateException();
            }

            state.schedulerOwnedBy = this;

            this.insertFresh(state);
        }
    }

    @Override
    public boolean cancel(final @NonNull SchedulableTick task) {
        throw new UnsupportedOperationException("Unsupported in CRS Scheduler");
    }

    @Override
    public void notifyTasks(final @NonNull SchedulableTick task) {
        if (task.state instanceof ScheduledState state) {
            state.scheduleTasks();
        }
    }

    public void markPinningUnsupported() {
        this.supportsPinning = false;
    }

    public boolean doesSupportPinning() {
        return this.supportsPinning;
    }

    public static final class ScheduledState {
        /**
         * Represents a constant indicating that the pinned state has not been set.
         * This value is used as a default or uninitialized state for tasks, and is
         * used for tasks that should be unpinned.
         */
        public static final int PINNED_NOT_SET = -1;
        private static final VarHandle PINNED_THREAD_ID_HANDLE =
            ConcurrentUtil.getVarHandle(ScheduledState.class, "pinnedThreadId", int.class);
        private static final int SCHEDULE_STATE_NOT_SCHEDULED = 0;
        private static final int SCHEDULE_STATE_SCHEDULED = 1;
        private static final int SCHEDULE_STATE_CANCELLED = 2;
        private final SchedulableTick tick;
        private final AtomicInteger scheduled = new AtomicInteger();
        public CRSThreadPool schedulerOwnedBy;
        private TickThreadRunner ownedBy;
        private int pinnedThreadId = PINNED_NOT_SET;
        private @Nullable Integer assocPartition = null;
        private boolean hasTasks;

        private LinkedSortedSet.Link<ScheduledState> awaitingLink;

        private ScheduledState(final SchedulableTick tick) {
            this.tick = tick;
        }

        private boolean tryMarkScheduled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_NOT_SCHEDULED, SCHEDULE_STATE_SCHEDULED);
        }

        private boolean tryMarkCancelled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_SCHEDULED, SCHEDULE_STATE_CANCELLED);
        }

        private boolean isScheduled() {
            return this.scheduled.get() == SCHEDULE_STATE_SCHEDULED;
        }

        public void scheduleTasks() {
            this.hasTasks = true;
        }

        public boolean compareHasTasks() {
            boolean orig = this.hasTasks;
            this.hasTasks = false;
            return orig;
        }

        public TickThreadRunner getOwnedBy() {
            return ownedBy;
        }

        public boolean canSteal(long nanos) {
            // diff + thresh <= 0L
            return (this.tick.getScheduledStart() - nanos) + schedulerOwnedBy.stealThresh <= 0L;
        }

        // TODO - store as tick thread ref
        public @Nullable Integer getPartitionKey() {
            return this.assocPartition;
        }

        public void setPartitionKey(final int partitionKey) {
            this.assocPartition = partitionKey;
        }

        public boolean isPinned() {
            return (int) PINNED_THREAD_ID_HANDLE.getVolatile(this) >= 0;
        }

        public int getPinnedThreadId() {
            return (int) PINNED_THREAD_ID_HANDLE.getVolatile(this);
        }

        public void setPinnedThreadId(final int threadId, final CRSThreadPool scheduler) {
            final int prev = (int) PINNED_THREAD_ID_HANDLE.getAndSet(this, threadId);

            // only update pin counts if actually changing
            if (prev == threadId) {
                return;
            }

            // try set scheduler if we haven't already
            if (this.schedulerOwnedBy == null) this.schedulerOwnedBy = scheduler;

            // need to handle the case where task is currently scheduled
            // and pinning changes - this requires scheduler intervention
            synchronized (scheduler.scheduleLock) {
                for (final TickThreadRunner runner : scheduler.runners) {
                    if (threadId < 0) {
                        // we are removing the pinning status
                        if (runner.id == prev && prev >= 0) {
                            runner.deincPin();
                            break;
                        }
                    } else {
                        // we are adding the pinning status
                        if (runner.id == threadId) {
                            // add new association
                            runner.incPin();
                        } else if (runner.id == prev && prev >= 0) {
                            // remove old association
                            runner.deincPin();
                        }
                    }
                }

                if (this.isScheduled() && this.ownedBy == null && this.awaitingLink == null) {
                    if (scheduler.queued.remove(this)) {
                        if (threadId >= 0) {
                            scheduler.queued.add(this, threadId);
                        } else {
                            scheduler.queued.add(this);
                        }
                    }
                }
            }
        }

        /**
         * Pins the task to a specific thread for execution or unpins it depending on the provided thread ID.
         * If the thread ID is invalid or not within the valid range of threads in the scheduler, no action is taken.
         * <p>
         * Use {@link #PINNED_NOT_SET} for unpinning a task
         * </p>
         *
         * @param threadId the ID of the thread to pin the task to. If the value is -1, the task will be unpinned.
         */
        public void pin(final int threadId, final CRSThreadPool scheduler) {
            if (threadId > scheduler.threadCount) {
                // don't pin, this isn't a valid thread
                return;
            } else if (threadId == PINNED_NOT_SET) {
                // we are unpinning
                unpin(scheduler);
                return;
            } else if (threadId < PINNED_NOT_SET) {
                // invalid thread, don't pin or unpin
                return;
            }
            this.setPinnedThreadId(threadId, scheduler);
        }

        public void unpin(final CRSThreadPool scheduler) {
            this.setPinnedThreadId(PINNED_NOT_SET, scheduler);
        }
    }

    public static final class TickThreadRunner implements Runnable {

        /**
         * There are no tasks in this thread's runqueue, so it is parked.
         * <p>
         * stateTarget = null
         * </p>
         */
        private static final int STATE_IDLE = 0;

        /**
         * The runner is waiting to tick a task, as it has no intermediate tasks to execute.
         * <p>
         * stateTarget = the task awaiting tick
         * </p>
         */
        private static final int STATE_AWAITING_TICK = 1;

        /**
         * The runner is executing a tick for one of the tasks that was in its runqueue.
         * <p>
         * stateTarget = the task being ticked
         * </p>
         */
        private static final int STATE_EXECUTING_TICK = 2;
        private static final VarHandle STATE_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "state", TickThreadRunnerState.class);
        private static final VarHandle LINKED_TO_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "linkedTo", ScheduledState.class);
        private static final VarHandle IS_PINNED_TO_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "pinnedTasks", int.class);
        public final int id;
        public final CRSThreadPool scheduler;
        public volatile Thread backingThread;
        private volatile TickThreadRunnerState state = new TickThreadRunnerState(null, STATE_IDLE);
        private volatile ScheduledState linkedTo = null;
        private int pinnedTasks = 0;

        public TickThreadRunner(final int id, final CRSThreadPool scheduler) {
            this.id = id;
            this.scheduler = scheduler;
        }

        private void incPin() {
            IS_PINNED_TO_HANDLE.getAndAdd(this, 1);
        }

        private void deincPin() {
            IS_PINNED_TO_HANDLE.getAndAdd(this, -1);
        }

        public boolean isPinnedTo() {
            return this.pinnedTasks > 0;
        }

        private void setStatePlain(final TickThreadRunnerState state) {
            STATE_HANDLE.set(this, state);
        }

        private void setStateOpaque(final TickThreadRunnerState state) {
            STATE_HANDLE.setOpaque(this, state);
        }

        private void setStateVolatile(final TickThreadRunnerState state) {
            STATE_HANDLE.setVolatile(this, state);
        }

        private void link(ScheduledState to) {
            LINKED_TO_HANDLE.setVolatile(this, to);
        }

        private void unlink() {
            LINKED_TO_HANDLE.setVolatile(this, null);
        }

        private Thread getRunnerThread() {
            return this.backingThread;
        }

        private void acceptTask(final @NonNull ScheduledState task) {
            if (task.ownedBy != null) {
                throw new IllegalStateException("Already owned by another runner");
            }
            task.ownedBy = this;
            final TickThreadRunnerState state = this.state;
            if (state.state != STATE_IDLE) {
                throw new IllegalStateException("Cannot accept task in state " + state);
            }
            this.setStateVolatile(new TickThreadRunnerState(task, STATE_AWAITING_TICK));
            LockSupport.unpark(this.getRunnerThread());
        }

        private void replaceTask(final ScheduledState task) {
            final TickThreadRunnerState state = this.state;
            if (state.state != STATE_AWAITING_TICK) {
                throw new IllegalStateException("Cannot replace task in state " + state);
            }
            if (task.ownedBy != null) {
                throw new IllegalStateException("Already owned by another runner");
            }
            task.ownedBy = this;

            state.stateTarget.ownedBy = null;

            this.setStateVolatile(new TickThreadRunnerState(task, STATE_AWAITING_TICK));
            LockSupport.unpark(this.getRunnerThread());
        }

        private void forceIdle() {
            final TickThreadRunnerState state = this.state;
            if (state.state != STATE_AWAITING_TICK) {
                throw new IllegalStateException("Cannot replace task in state " + state);
            }
            state.stateTarget.ownedBy = null;
            this.setStateOpaque(new TickThreadRunnerState(null, STATE_IDLE));
            // no need to unpark
        }

        private boolean takeTask(final TickThreadRunnerState state, final ScheduledState task) {
            synchronized (this.scheduler.scheduleLock) {
                if (this.state != state) {
                    return false;
                }
                this.setStatePlain(new TickThreadRunnerState(task, STATE_EXECUTING_TICK));
                this.scheduler.takeTask(this, task);
                return true;
            }
        }

        private void returnTask(final @NonNull ScheduledState task, final boolean reschedule) {
            synchronized (this.scheduler.scheduleLock) {
                task.ownedBy = null;

                final ScheduledState newWait = this.scheduler.returnTask(this, reschedule && task.isScheduled() ? task : null);
                if (newWait == null) {
                    this.setStatePlain(new TickThreadRunnerState(null, STATE_IDLE));
                } else {
                    if (newWait.ownedBy != null) {
                        throw new IllegalStateException("Already owned by another runner");
                    }
                    newWait.ownedBy = this;
                    this.setStatePlain(new TickThreadRunnerState(newWait, STATE_AWAITING_TICK));
                }
            }
        }

        @Override
        public void run() {
            this.backingThread = Thread.currentThread();

            main_state_loop:
            for (; ; ) {
                final TickThreadRunnerState startState = this.state;
                final int startStateType = startState.state;
                final ScheduledState startStateTask = startState.stateTarget;

                if (this.scheduler.halted) {
                    return;
                }

                switch (startStateType) {
                    case STATE_IDLE: {
                        while (this.state.state == STATE_IDLE) {
                            Thread.interrupted();
                            LockSupport.park();
                            if (this.scheduler.halted) {
                                return;
                            }
                        }
                        continue main_state_loop;
                    }

                    case STATE_AWAITING_TICK: {
                        final long deadline = startStateTask.tick.getScheduledStart();

                        for (; ; ) {
                            if (this.state != startState) {
                                continue main_state_loop;
                            }
                            final long diff = deadline - System.nanoTime();
                            if (diff <= 0L) {
                                break;
                            }
                            // we are parking, which is fine, however, we
                            // CAN do mid-tick tasks here instead, which makes us
                            // much more productive
                            if ((diff > scheduler.runTaskBuff) && // if we are less than the buffer, then don't try run mid-tick-tasks
                                (startStateTask.compareHasTasks() || startStateTask.tick.hasTasks())) {
                                // try and take the tick task like it's a normal tick
                                if (!this.takeTask(startState, startStateTask)) {
                                    // just park like normal, couldn't take task
                                    LockSupport.parkNanos(startState, diff);
                                    continue; // done parking, or we got woken up, so we continue so it loops back to check the diff
                                }
                                // task is taken, wonderful
                                final long bufferedDeadline = deadline - scheduler.runTaskBuff;
                                final boolean taskRes = startStateTask.tick.runTasks(
                                    () -> !scheduler.halted && (bufferedDeadline - System.nanoTime()) <= 0L
                                );
                                // return the task to the global queue. we will try and take it later(if rescheduled), which CAN be picked up
                                // by a different thread, but if that does happen, it doesn't entirely matter
                                this.returnTask(startStateTask, taskRes);
                                // either this was rescheduled, which means it won't run on the next tick,
                                // or it finished all tasks, and we reloop again. we return to the main state loop,
                                // though in the case of the tick being picked up by a different thread
                                continue main_state_loop;
                            }
                            LockSupport.parkNanos(startState, diff);
                            if (scheduler.halted) {
                                return;
                            }
                        }

                        if (!this.takeTask(startState, startStateTask)) {
                            // couldn't take task, continue loop
                            continue main_state_loop;
                        }

                        try {
                            final boolean reschedule = startStateTask.tick.runTick();
                            this.returnTask(startStateTask, reschedule);
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to tick task", e);
                        }

                        continue main_state_loop; // finished tick, continue
                    }

                    case STATE_EXECUTING_TICK: {
                        throw new IllegalStateException("Tick execution must be set by runner thread, not by any other thread");
                    }

                    default: {
                        throw new IllegalStateException("Unknown state: " + startState);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "CRSThread-" + id;
        }

        private record TickThreadRunnerState(ScheduledState stateTarget, int state) {
        }
    }

    @NullMarked
    private final class StealingQueue {

        private final TickPriorityQueue<ScheduledState> queue;

        public StealingQueue(Comparator<ScheduledState> comparator) {
            this.queue = new TickPriorityQueue<>(150, comparator, ScheduledState.class);
        }

        public void add(ScheduledState state) {
            queue.offer(state);
        }

        public void add(ScheduledState state, int partitionKey) {
            state.setPartitionKey(partitionKey);
            queue.offer(state);
        }

        public boolean isEmpty() {
            return queue.size == 0;
        }

        public boolean remove(ScheduledState state) {
            return queue.remove(state);
        }

        public @Nullable ScheduledState pollFor(final int partitionKey, final boolean runnerIsPinned) {
            long initialPollTimeNanos = System.nanoTime();
            for (int i = 0; i < queue.size; i++) {
                ScheduledState element = queue.arr[i];
                final Integer assoc = element.getPartitionKey();
                final boolean canSteal = element.canSteal(initialPollTimeNanos);
                if (assoc == null || assoc == partitionKey || canSteal) {
                    final boolean taskIsPinned = element.isPinned();

                    // tasks pinned to the runner
                    if (taskIsPinned && element.getPinnedThreadId() == partitionKey) {
                        remove(element);
                        return element; // stop iterating
                    }

                    // work stealing is prioritized, since if we are stealing, it means
                    // the task that is being stolen has missed its deadline...
                    if (!runnerIsPinned && !taskIsPinned && canSteal) {
                        remove(element);
                        return element; // stop iterating
                    }

                    // fallback to local work
                    if (!runnerIsPinned && !taskIsPinned) {
                        remove(element);
                        return element; // stop iterating
                    }
                }
            }
            // no matches
            return null;
        }
    }
}
