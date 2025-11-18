package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.set.LinkedSortedSet;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import io.canvasmc.canvas.Config;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Original class from ConcurrentUtil {@link ca.spottedleaf.concurrentutil.scheduler.SchedulerThreadPool}
 * <p>
 * Modified by Dueris further to introduce optimizations and region pinning
 * </p>
 *
 * @author Dueris
 * @author Spottedleaf
 */
public class SchedulerTickTaskThreadPool {

    public static final long DEADLINE_NOT_SET = Long.MIN_VALUE;
    public static final long RUN_TASKS_BUFFER_NANOS = (long) (Config.INSTANCE.scheduler.runTasksBufferMillis * 1_000_000L);
    public static final long STEAL_THRESH_NANOS = Config.INSTANCE.scheduler.stealThresholdMillis * 1_000_000L;

    private static final Comparator<SchedulableTick> TICK_COMPARATOR_BY_TIME = (final SchedulableTick t1, final SchedulableTick t2) -> {
        final int timeCompare = TimeUtil.compareTimes(t1.scheduledStart, t2.scheduledStart);
        if (timeCompare != 0) {
            return timeCompare;
        }

        return Long.compare(t1.id, t2.id);
    };
    private final LinkedSortedSet<SchedulableTick> awaiting = new LinkedSortedSet<>(TICK_COMPARATOR_BY_TIME);
    private final PartitionedPriorityQueue<SchedulableTick> queued = new PartitionedPriorityQueue<>(TICK_COMPARATOR_BY_TIME, SchedulableTick.class);
    private final Object scheduleLock = new Object();
    private final ThreadFactory threadFactory;
    private final int threadCount;
    private TickThreadRunner[] runners;
    private Thread[] threads;
    private BitSet idleThreads;
    private volatile boolean halted;

    /**
     * Creates, but does not start, a scheduler thread pool with the specified number of threads
     * created using the specified thread factory.
     *
     * @param threadFactory Specified thread factory
     * @see #startThreadPool(int)
     */
    public SchedulerTickTaskThreadPool(final int threads, final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.startThreadPool(threads);
        this.threadCount = threads;
    }

    /**
     * Starts the scheduler thread pool.
     *
     * @param threads thread count
     */
    private void startThreadPool(int threads) {
        synchronized (this) {
            if (this.halted) {
                return;
            }

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

            for (final Thread thread : this.threads) {
                thread.start();
            }
        }
    }

    /**
     * Retrieves the number of threads currently configured for the scheduler.
     *
     * @return The number of threads in the scheduler.
     */
    public int threadCount() {
        return this.threadCount;
    }

    /**
     * Attempts to prevent further execution of tasks, optionally waiting for the scheduler threads to die.
     *
     * @param sync      Whether to wait for the scheduler threads to die.
     * @param maxWaitNS The maximum time, in ns, to wait for the scheduler threads to die.
     * @return {@code true} if sync was false, or if sync was true and the scheduler threads died before the timeout.
     * Otherwise, returns {@code false} if the time elapsed exceeded the maximum wait time.
     */
    public boolean halt(final boolean sync, final long maxWaitNS) {
        this.halted = true;
        for (final Thread thread : this.threads) {
            // force response to halt
            LockSupport.unpark(thread);
        }
        final long time = System.nanoTime();
        if (sync) {
            // start at 10 * 0.5ms -> 5ms
            for (long failures = 9L; ; failures = ConcurrentUtil.linearLongBackoff(failures, 500_000L, 50_000_000L)) {
                boolean allDead = true;
                for (final Thread thread : this.threads) {
                    if (thread.isAlive()) {
                        allDead = false;
                        break;
                    }
                }
                if (allDead) {
                    return true;
                }
                if ((System.nanoTime() - time) >= maxWaitNS) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns an array of the underlying scheduling threads.
     */
    public Thread[] getThreads() {
        return this.threads.clone();
    }

    private void insertFresh(final @NotNull SchedulableTick task) {
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
                    final SchedulableTick currentTask = pinnedState.stateTarget;

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
        final SchedulableTick last = this.awaiting.last();

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

    private void takeTask(final TickThreadRunner runner, final SchedulableTick tick) {
        if (!this.awaiting.remove(tick.awaitingLink)) {
            throw new IllegalStateException("Task is not in awaiting");
        }
        tick.awaitingLink = null;
    }

    private SchedulableTick returnTask(final TickThreadRunner runner, final SchedulableTick reschedule) {
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

                    // send task to idle runner
                    reschedule.awaitingLink = this.awaiting.addLast(reschedule);
                    targetRunner.acceptTask(reschedule);
                }
            } else {
                this.queued.add(reschedule, runner.id);
            }
        }

        SchedulableTick ret = null;
        if (!this.queued.isEmpty()) {
            // snapshot the elements, essentially the same as making
            // a reusable and quick iterator with this partition queue
            SchedulableTick[] elements = this.queued.elements(runner.id);
            final boolean runnerIsPinned = runner.isPinnedTo();

            for (final SchedulableTick task : elements) {
                final boolean taskIsPinned = task.isPinned();

                // tasks pinned to the runner
                if (taskIsPinned && task.getPinnedThreadId() == runner.id) {
                    this.queued.remove(task);
                    ret = task;
                    break;
                }

                // work stealing is prioritized, since if we are stealing, it means
                // the task that is being stolen has missed its deadline...
                if (!runnerIsPinned && !taskIsPinned && task.isGloballyVisible()) {
                    this.queued.remove(task);
                    ret = task;
                    break;
                }

                // fallback to local work
                if (!runnerIsPinned && !taskIsPinned) {
                    this.queued.remove(task);
                    ret = task;
                    break;
                }
            }
        }

        if (ret == null) {
            this.idleThreads.set(runner.id);
        } else {
            ret.awaitingLink = this.awaiting.addLast(ret);
        }

        return ret;
    }

    /**
     * Schedules the specified task to be executed on this thread pool.
     *
     * @param task Specified task
     * @throws IllegalStateException If the task is already scheduled
     * @see SchedulableTick
     */
    public void schedule(final SchedulableTick task) {
        synchronized (this.scheduleLock) {
            if (!task.tryMarkScheduled()) {
                throw new IllegalStateException("Task " + task + " is already scheduled or cancelled");
            }

            if (task.schedulerOwnedBy != this) {
                throw new IllegalStateException("Task was created by a different scheduler");
            }

            this.insertFresh(task);
        }
    }

    /**
     * Indicates that intermediate tasks are available to be executed by the task.
     * <p>
     * Note: currently a no-op
     * </p>
     *
     * @param task The specified task
     * @see SchedulableTick
     */
    public void notifyTasks(final @NotNull SchedulableTick task) {
        task.setMarkedAsHasTasks(true);
        // if ownedBy is non-null, then the thread is already awake
        /* final TickThreadRunner runner = task.ownedBy;
        if (runner == null) {
            return;
        }
        LockSupport.unpark(runner.getRunnerThread()); */
    }

    /**
     * Represents a tickable task that can be scheduled into a {@link SchedulerTickTaskThreadPool}.
     * <p>
     * A tickable task is expected to run on a fixed interval, which is determined by
     * the {@link SchedulerTickTaskThreadPool}.
     * </p>
     * <p>
     * A tickable task can have intermediate tasks that can be executed before its tick method is ran. Instead of
     * the {@link SchedulerTickTaskThreadPool} parking in-between ticks, the scheduler will instead drain
     * intermediate tasks from scheduled tasks. The parsing of intermediate tasks allows the scheduler to take
     * advantage of downtime to reduce the intermediate task load from tasks once they begin ticking.
     * </p>
     * <p>
     * It is guaranteed that {@link #runTick()} and {@link #runTasks(BooleanSupplier)} are never
     * invoked in parallel.
     * It is required that when intermediate tasks are scheduled, that {@link SchedulerTickTaskThreadPool#notifyTasks(SchedulableTick)}
     * is invoked for any scheduled task - otherwise, {@link #runTasks(BooleanSupplier)} may not be invoked to
     * parse intermediate tasks.
     * </p>
     */
    public static abstract class SchedulableTick implements PartitionedPriorityQueue.Partitionable {
        /**
         * Represents a constant indicating that the pinned state has not been set.
         * This value is used as a default or uninitialized state for tasks, and is
         * used for tasks that should be unpinned.
         */
        public static final int PINNED_NOT_SET = -1;
        private static final AtomicLong ID_GENERATOR = new AtomicLong();
        private static final int SCHEDULE_STATE_NOT_SCHEDULED = 0;
        private static final int SCHEDULE_STATE_SCHEDULED = 1;
        private static final int SCHEDULE_STATE_CANCELLED = 2;
        private static final VarHandle PINNED_THREAD_ID_HANDLE =
            ConcurrentUtil.getVarHandle(SchedulableTick.class, "pinnedThreadId", int.class);
        private static final VarHandle MARKED_HAS_TASKS =
            ConcurrentUtil.getVarHandle(SchedulableTick.class, "markedAsHasTasks", boolean.class);
        public final long id = ID_GENERATOR.getAndIncrement();
        private final AtomicInteger scheduled = new AtomicInteger();
        public boolean markedAsHasTasks = false;
        private final SchedulerTickTaskThreadPool schedulerOwnedBy;
        private long scheduledStart = DEADLINE_NOT_SET;
        private TickThreadRunner ownedBy;
        private int pinnedThreadId = PINNED_NOT_SET;
        private LinkedSortedSet.Link<SchedulableTick> awaitingLink;

        public SchedulableTick(final SchedulerTickTaskThreadPool schedulerOwnedBy) {
            this.schedulerOwnedBy = schedulerOwnedBy;
        }

        @Override
        public boolean isGloballyVisible() {
            // diff + thresh <= 0L
            return (this.getScheduledStart() - System.nanoTime()) + STEAL_THRESH_NANOS <= 0L;
        }

        public final boolean isPinned() {
            return (int) PINNED_THREAD_ID_HANDLE.getVolatile(this) >= 0;
        }

        public final int getPinnedThreadId() {
            return (int) PINNED_THREAD_ID_HANDLE.getVolatile(this);
        }

        public final void setPinnedThreadId(final int threadId) {
            final int prev = (int) PINNED_THREAD_ID_HANDLE.getAndSet(this, threadId);

            // only update pin counts if actually changing
            if (prev == threadId) {
                return;
            }

            final SchedulerTickTaskThreadPool scheduler = this.schedulerOwnedBy;

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

        public final void setMarkedAsHasTasks(final boolean marked) {
            MARKED_HAS_TASKS.setRelease(this, marked);
        }

        public final boolean tryMarkTakingTasks() {
            return (boolean) MARKED_HAS_TASKS.getAndSet(this, false);
        }

        /**
         * Pins the task to a specific thread for execution or unpins it depending on the provided thread ID.
         * If the thread ID is invalid or not within the valid range of threads in the scheduler, no action is taken.
         * <p>
         * Use {@link #PINNED_NOT_SET} for unpinning a task
         * </p>
         * @param threadId the ID of the thread to pin the task to. If the value is -1, the task will be unpinned.
         */
        public final void pin(final int threadId) {
            if (threadId > this.schedulerOwnedBy.threadCount()) {
                // don't pin, this isn't a valid thread
                return;
            } else if (threadId == PINNED_NOT_SET) {
                // we are unpinning
                unpin();
                return;
            } else if (threadId < PINNED_NOT_SET) {
                // invalid thread, don't pin or unpin
                return;
            }
            this.setPinnedThreadId(threadId);
        }

        public final void unpin() {
            this.setPinnedThreadId(PINNED_NOT_SET);
        }

        public TickThreadRunner getOwnedBy() {
            return ownedBy;
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

        protected final long getScheduledStart() {
            return this.scheduledStart;
        }

        /**
         * If this task is scheduled, then this may only be invoked during {@link #runTick()},
         * and {@link #runTasks(BooleanSupplier)}
         */
        public final void setScheduledStart(final long value) {
            this.scheduledStart = value;
        }

        /**
         * Executes the tick.
         * <p>
         * It is the callee's responsibility to invoke {@link #setScheduledStart(long)} to adjust the start of
         * the next tick.
         * </p>
         *
         * @return {@code true} if the task should continue to be scheduled, {@code false} otherwise.
         */
        public abstract boolean runTick();

        /**
         * Returns whether this task has any intermediate tasks that can be executed.
         */
        public abstract boolean hasTasks();

        /**
         * Returns {@code null} if this task should not be scheduled, otherwise returns
         * {@code Boolean.TRUE} if there are more intermediate tasks to execute and
         * {@code Boolean.FALSE} if there are no more intermediate tasks to execute.
         */
        public abstract Boolean runTasks(final BooleanSupplier canContinue);

        @Override
        public String toString() {
            return "SchedulableTick:{" +
                "class=" + this.getClass().getName() + "," +
                "scheduled_state=" + this.scheduled.get() + ","
                + "}";
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
        public final int id;
        public final SchedulerTickTaskThreadPool scheduler;
        public volatile Thread thread;
        private TickThreadRunnerState state = new TickThreadRunnerState(null, STATE_IDLE);
        private int pinnedTasks = 0;
        private static final VarHandle IS_PINNED_TO_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "pinnedTasks", int.class);

        public TickThreadRunner(final int id, final SchedulerTickTaskThreadPool scheduler) {
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

        private Thread getRunnerThread() {
            return this.thread;
        }

        private void acceptTask(final SchedulableTick task) {
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

        private void replaceTask(final SchedulableTick task) {
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

        private boolean takeTask(final TickThreadRunnerState state, final SchedulableTick task) {
            synchronized (this.scheduler.scheduleLock) {
                if (this.state != state) {
                    return false;
                }
                this.setStatePlain(new TickThreadRunnerState(task, STATE_EXECUTING_TICK));
                this.scheduler.takeTask(this, task);
                return true;
            }
        }

        private void returnTask(final SchedulableTick task, final boolean reschedule) {
            synchronized (this.scheduler.scheduleLock) {
                task.ownedBy = null;

                final SchedulableTick newWait = this.scheduler.returnTask(this, reschedule && task.isScheduled() ? task : null);
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
            this.thread = Thread.currentThread();

            main_state_loop:
            for (;;) {
                final TickThreadRunnerState startState = this.state;
                final int startStateType = startState.state;
                final SchedulableTick startStateTask = startState.stateTarget;

                if (this.scheduler.halted) {
                    return;
                }

                switch (startStateType) {
                    case STATE_IDLE: {
                        while (this.state.state == STATE_IDLE) {
                            LockSupport.park();
                            if (this.scheduler.halted) {
                                return;
                            }
                        }
                        continue main_state_loop;
                    }

                    case STATE_AWAITING_TICK: {
                        final long deadline = startStateTask.getScheduledStart();
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
                            if (startStateTask.tryMarkTakingTasks() || startStateTask.hasTasks()) {
                                // try and take the tick task like it's a normal tick
                                if (!this.takeTask(startState, startStateTask)) {
                                    // just park like normal, couldn't take task
                                    LockSupport.parkNanos(startState, diff);
                                    continue; // done parking, or we got woken up, so we continue so it loops back to check the diff
                                }
                                // task is taken, wonderful
                                final long bufferedDeadline = deadline - RUN_TASKS_BUFFER_NANOS;
                                final Boolean taskRes = startStateTask.runTasks(
                                    () -> !this.scheduler.halted && (bufferedDeadline - System.nanoTime()) <= 0L
                                );
                                // return the task to the global queue. we will try and take it later(if rescheduled), which CAN be picked up
                                // by a different thread, but if that does happen, it doesn't entirely matter
                                this.returnTask(startStateTask, taskRes != null);
                                // either this was rescheduled, which means it won't run on the next tick,
                                // or it finished all tasks, and we reloop again. we return to the main state loop,
                                // though in the case of the tick being picked up by a different thread
                                continue main_state_loop;
                            }
                            LockSupport.parkNanos(startState, diff);
                            if (this.scheduler.halted) {
                                return;
                            }
                        }

                        if (!this.takeTask(startState, startStateTask)) {
                            // couldn't take task, continue loop
                            continue main_state_loop;
                        }

                        // TODO - exception handling?
                        final boolean reschedule = startStateTask.runTick();
                        this.returnTask(startStateTask, reschedule);

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

        private record TickThreadRunnerState(SchedulableTick stateTarget, int state) {
        }
    }

    /**
     * A priority queue that associates elements with partition keys (integers) and allows selective operations
     * based on those partitions. This class provides management of elements within specific partitions,
     * while still retaining the behavior of a priority queue.
     *
     * @param <E> The type of elements stored in the queue, which must implement the {@link PartitionedPriorityQueue.Partitionable} interface.
     * @author dueris
     */
    private static class PartitionedPriorityQueue<E extends PartitionedPriorityQueue.Partitionable> {

        private final PriorityQueue<E> queue;
        private final Map<E, Integer> associations;
        private final List<E> tempBuffer;
        private final Class<? extends E> elementClass;

        public PartitionedPriorityQueue(Comparator<? super E> comparator, Class<? extends E> elementClass) {
            this.queue = new PriorityQueue<>(comparator);
            this.associations = new Object2ObjectOpenHashMap<>();
            this.tempBuffer = new ObjectArrayList<>(16);
            this.elementClass = elementClass;
        }

        /**
         * Adds the specified element to this queue
         * <p>
         * <b>Note:</b> This task will be visible globally
         * </p>
         */
        public void add(E e) {
            if (associations.containsKey(e) || queue.contains(e)) {
                throw new IllegalArgumentException("Duplicate element: " + e);
            }

            queue.add(e);
        }

        /**
         * Adds the specified element to this queue
         * <p>
         * <b>Note:</b> This task will be visible specifically to the partition key, unless {@link Partitionable#isGloballyVisible()} is true.
         * </p>
         * <br>
         * If there is potential for this to be owned by a different partition key,
         * remove it globally first, then call this.
         */
        public void add(E e, int partitionKey) {
            Integer existing = associations.get(e);
            if (existing != null || queue.contains(e)) {
                throw new IllegalArgumentException("Duplicate element: " + e);
            }

            associations.put(e, partitionKey);
            queue.add(e);
        }


        public E poll(int partitionKey) {
            E head = queue.peek();
            if (head != null) {
                Integer assoc = associations.get(head);
                if (assoc == null || assoc == partitionKey || head.isGloballyVisible()) {
                    queue.poll();
                    associations.remove(head);
                    return head;
                }
            }

            tempBuffer.clear();
            E result = null;

            while (!queue.isEmpty()) {
                E element = queue.poll();
                Integer assoc = associations.get(element);

                if (assoc == null || assoc == partitionKey || element.isGloballyVisible()) {
                    result = element;
                    associations.remove(element);
                    break;
                } else {
                    tempBuffer.add(element);
                }
            }

            if (!tempBuffer.isEmpty()) {
                queue.addAll(tempBuffer);
            }

            return result;
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public boolean remove(E element) {
            boolean removed = queue.remove(element);
            if (removed) {
                associations.remove(element);
            }
            return removed;
        }

        public E[] elements(final int partitionKey) {
            final ObjectArrayList<E> snapshot = new ObjectArrayList<>(queue.size());

            // this is safe, held under scheduling lock
            for (E element : queue) {
                Integer assoc = associations.get(element);
                if (assoc == null || assoc == partitionKey || element.isGloballyVisible()) {
                    snapshot.add(element);
                }
            }

            @SuppressWarnings("unchecked")
            E[] result = (E[]) Array.newInstance(this.elementClass, snapshot.size());
            snapshot.toArray(result);
            return result;
        }

        public interface Partitionable {
            /**
             * Called every time the element is being searched to determine if it should
             * be visible to all partitions regardless of its association.
             *
             * @return true if this element should be globally visible
             */
            boolean isGloballyVisible();
        }
    }
}
