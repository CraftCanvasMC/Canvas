package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import io.canvasmc.canvas.Config;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.NonNull;

/**
 * Scheduler thread pool implementation based off EDF scheduler in ConcurrentUtil
 * that supports intermediate task execution and task linking
 */
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
    private final StealingQueue stealingQueue = new StealingQueue();
    private final Object scheduleLock = new Object();
    private final int threadCount;
    private final TickThreadRunner[] runners;
    private final Thread[] threads;
    private final BitSet idleThreads;

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

        if (task.linked != null) {
            if (idleThreads.get(task.linked.id)) {
                // is idle
                idleThreads.clear(task.linked.id);
                task.linked.acceptTask(task);
            } else {
                // not idle, just queue
                task.linked.directQueue.add(task);
            }
            return;
        }

        final int firstIdleThread = this.idleThreads.nextSetBit(0);

        if (firstIdleThread != -1) {
            // push to idle thread
            this.idleThreads.clear(firstIdleThread);
            final TickThreadRunner runner = runners[firstIdleThread];
            runner.acceptTask(task);
            return;
        }

        // add to global queue, will be picked up later
        this.stealingQueue.addGlobal(task);
    }

    private ScheduledState returnTask(final TickThreadRunner runner, final ScheduledState reschedule) {
        if (reschedule != null) {
            // reschedule shouldn't be in any queue
            if (reschedule.linked != null) {
                if (reschedule.linked == runner) {
                    // reschedule linked to the live runner
                    runner.directQueue.add(reschedule);
                } else {
                    // linked to a diff runner, if idle, wake and take
                    if (idleThreads.get(reschedule.linked.id)) {
                        // push to idle thread
                        idleThreads.clear(reschedule.linked.id);
                        reschedule.linked.acceptTask(reschedule);
                    } else
                        // not idle, just queue it, will be picked up next tick
                        reschedule.linked.directQueue.add(reschedule);
                }
            } else this.stealingQueue.add(reschedule, runner);
        }

        ScheduledState ret = this.stealingQueue.poll(runner);

        if (ret == null) {
            this.idleThreads.set(runner.id);
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
        private static final int SCHEDULE_STATE_NOT_SCHEDULED = 0;
        private static final int SCHEDULE_STATE_SCHEDULED = 1;
        private static final int SCHEDULE_STATE_CANCELLED = 2;
        private final SchedulableTick tick;
        private final AtomicInteger scheduled = new AtomicInteger();
        public CRSThreadPool schedulerOwnedBy;
        private volatile boolean hasTasks;

        private TickThreadRunner ownedBy;
        public TickThreadRunner linked;

        private ScheduledState(final SchedulableTick tick) {
            this.tick = tick;
        }

        private boolean tryMarkScheduled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_NOT_SCHEDULED, SCHEDULE_STATE_SCHEDULED);
        }

        private boolean tryMarkCancelled() {
            return this.scheduled.compareAndSet(SCHEDULE_STATE_SCHEDULED, SCHEDULE_STATE_CANCELLED);
        }

        boolean isScheduled() {
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

        public void link(final TickThreadRunner threadRunner) {
            if (threadRunner == null) throw new IllegalStateException("Use unlink");
            synchronized (threadRunner.scheduler.scheduleLock) {
                if (linked != null) throw new IllegalStateException("Already linked");
                linked = threadRunner;
                TickThreadRunner.LINKED_HANDLE.getAndAdd(linked, 1);
            }
        }

        public void unlink() {
            synchronized (schedulerOwnedBy.scheduleLock) {
                if (linked == null) throw new IllegalStateException("Not linked");
                TickThreadRunner.LINKED_HANDLE.getAndAdd(linked, -1);
                linked = null;
            }
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
        private static final VarHandle LINKED_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "linked", int.class);
        public final int id;
        public final CRSThreadPool scheduler;
        public volatile Thread backingThread;
        private volatile TickThreadRunnerState state = new TickThreadRunnerState(null, STATE_IDLE);

        private final ObjectPriorityQueue<ScheduledState> localQueue = new ObjectPriorityQueue<>(65, TICK_COMPARATOR_BY_TIME);
        private final ObjectPriorityQueue<ScheduledState> directQueue = new ObjectPriorityQueue<>(2, TICK_COMPARATOR_BY_TIME);
        private volatile int linked = 0;

        public TickThreadRunner(final int id, final CRSThreadPool scheduler) {
            this.id = id;
            this.scheduler = scheduler;
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

        private boolean takeTask(final TickThreadRunnerState state, final ScheduledState task) {
            synchronized (this.scheduler.scheduleLock) {
                if (this.state != state) {
                    return false;
                }
                this.setStatePlain(new TickThreadRunnerState(task, STATE_EXECUTING_TICK));
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
                                    () -> !this.scheduler.halted && (bufferedDeadline - System.nanoTime()) <= 0L
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
                            if (this.scheduler.halted) {
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

    private class StealingQueue {

        private final ObjectPriorityQueue<ScheduledState> globalQueue;

        public StealingQueue() {
            this.globalQueue = new ObjectPriorityQueue<>(100, TICK_COMPARATOR_BY_TIME);
        }

        public void addGlobal(@NonNull ScheduledState task) {
            if (task.linked != null) {
                task.linked.directQueue.add(task);
                return;
            }
            globalQueue.add(task);
        }

        public void add(final @NonNull ScheduledState state, final @NonNull TickThreadRunner threadRunner) {
            if (state.linked != null && threadRunner != state.linked) {
                // state is linked, and is trying to be added to the wrong queue
                throw new IllegalStateException("Linked to different thread");
            } else if (state.linked != null) {
                threadRunner.directQueue.add(state);
            } else threadRunner.localQueue.add(state);
        }

        // note: try pull from global first, if global head is sooner, return, else, if local
        //       is overdue, return, else, try steal if more overdue than local head
        public ScheduledState poll(final @NonNull TickThreadRunner runner) {
            if (runner.linked > 0) {
                return runner.directQueue.poll();
            }
            // check global first, this is all under lock
            final ScheduledState localHead = runner.localQueue.peek();
            if (globalQueue.size > 0) {
                ScheduledState globalHead = globalQueue.peek();
                if (localHead == null || TICK_COMPARATOR_BY_TIME.compare(globalHead, localHead) < 0) {
                    // global is better, take that
                    return globalQueue.poll();
                }
            }

            final TickThreadRunner[] runners = CRSThreadPool.this.runners;
            final long nanos = System.nanoTime();

            if (localHead != null) {
                long localTime = localHead.tick.scheduledStart;

                // if local is overdue by more than steal threshold, just take it
                if ((nanos - localTime) > stealThresh) {
                    return runner.localQueue.poll();
                }
            }

            // try steal next
            for (int i = 0; i < threadCount; i++) {
                if (i == runner.id) continue;

                ScheduledState otherHead = runners[i].localQueue.peek();
                if (otherHead != null && otherHead.canSteal(nanos) && (localHead == null || TICK_COMPARATOR_BY_TIME.compare(otherHead, localHead) < 0)) {
                    // is stealable, just fucking grab it, it's overdue more than local head
                    return runners[i].localQueue.poll();
                }
            }

            // try and get local head, this is best
            return runner.localQueue.poll();
        }
    }
}
