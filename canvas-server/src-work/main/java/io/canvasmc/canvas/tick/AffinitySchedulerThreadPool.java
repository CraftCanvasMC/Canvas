package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.util.CpuInfoReport;
import io.canvasmc.canvas.util.collection.FastHeapPriorityQueue;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler thread pool implementation that uses EDF scheduling, based off {@link ca.spottedleaf.concurrentutil.scheduler.EDFSchedulerThreadPool}
 * <p>
 *     Intermediate task execution is supported in this scheduler.
 * </p>
 * <p>
 *     NUMA aware scheduling is not supported in this scheduler.
 * </p>
 * <p>
 *     CPU affinity is supported in this scheduler
 * </p>
 * @author dueris, spottedleaf
 */
public final class AffinitySchedulerThreadPool extends Scheduler {

    public static final long DEFAULT_STEAL_THRESH_MILLIS = 3L;
    public static final double DEFAULT_RUN_TASKS_BUFFER_MILLIS = (double) 100_000 / 1_000_000;

    private static final Comparator<AffinitySchedulerThreadPool.ScheduledState> TICK_COMPARATOR_BY_TIME = (final AffinitySchedulerThreadPool.ScheduledState s1, final AffinitySchedulerThreadPool.ScheduledState s2) -> {
        final SchedulableTick t1 = s1.tick;
        final SchedulableTick t2 = s2.tick;

        final int timeCompare = TimeUtil.compareTimes(t1.scheduledStart, t2.scheduledStart);
        if (timeCompare != 0) {
            return timeCompare;
        }

        return Long.signum(t1.id - t2.id);
    };

    private static final Logger LOGGER = LoggerFactory.getLogger("Scheduler");

    private final TickThreadRunner[] runners;
    private final Thread[] threads;
    private final BitSet idleThreads;

    private final FastHeapPriorityQueue<ScheduledState> globalQueue = new FastHeapPriorityQueue<>(100, TICK_COMPARATOR_BY_TIME, ScheduledState.class);

    private final Object scheduleLock = new Object();
    private final long runTaskBuff;
    private final long stealThresh;
    private final BooleanSupplier linkingSupported;
    private final boolean enableWorkStealing;
    private final boolean enableIntermediateTasks;
    private final Consumer<Throwable> onException;

    private final java.util.concurrent.atomic.AtomicInteger nextSteal = new java.util.concurrent.atomic.AtomicInteger(0);

    private volatile boolean halted;

    public AffinitySchedulerThreadPool(
        final int threads,
        final ThreadFactory threadFactory,
        long runTaskBuff,
        long stealThresh,
        BooleanSupplier linkingSupported,
        boolean enableWorkStealing,
        boolean enableAffinity,
        boolean enableIntermediateTasks,
        Consumer<Throwable> onException
    ) {
        this.runTaskBuff = runTaskBuff;
        this.stealThresh = stealThresh;
        this.linkingSupported = linkingSupported;
        this.enableWorkStealing = enableWorkStealing;
        this.enableIntermediateTasks = enableIntermediateTasks;
        this.onException = onException;
        final BitSet idleThreads = new BitSet(threads);
        for (int i = 0; i < threads; ++i) {
            idleThreads.set(i);
        }
        this.idleThreads = idleThreads;

        final BitSet affinitySet;
        if (enableAffinity) {
            if (!CpuInfoReport.isLinux()) {
                LOGGER.warn("Affinity setting is only supported on Linux");
                affinitySet = new BitSet();
            }
            else {
                affinitySet = getAffinity(GlobalConfiguration.getInstance().regionScheduler.affinityScheduler.tickRegionAffinity);
            }
        }
        else affinitySet = new BitSet();
        if (!affinitySet.isEmpty()) {
            if (affinitySet.cardinality() < threads) throw new IllegalArgumentException("Affinity setting needs 1 allocated core per tick thread");
            LOGGER.info("Affinity scheduler now bound to: {}", affinitySet);
        }

        final TickThreadRunner[] runners = new TickThreadRunner[threads];
        final Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; ++i) {
            int cpuId = affinitySet.isEmpty() ? -1 : affinitySet.nextSetBit(0);
            if (cpuId != -1) affinitySet.clear(cpuId);
            runners[i] = new TickThreadRunner(i, this, cpuId);
            t[i] = threadFactory.newThread(runners[i]);
        }

        this.threads = t;
        this.runners = runners;
    }

    private @NonNull BitSet getAffinity(int @NonNull [] affinity) {
        if (affinity.length == 0) {
            LOGGER.warn("No affinity set configured, backing off, logging CPU topology:\n{}", CpuInfoReport.compileOutput());
            return new BitSet();
        }
        int maxAvailable = Runtime.getRuntime().availableProcessors();
        BitSet affinitySet = new BitSet(affinity.length);
        Arrays.stream(affinity)
            .distinct()
            .filter(cpuId -> {
                // don't log parse error cpus
                if (cpuId == -1) return false;
                if (cpuId >= 0 && cpuId < maxAvailable) {
                    return true;
                } else {
                    LOGGER.error("Invalid cpu id {}, ignoring.", cpuId);
                    return false;
                }
            })
            .forEach(affinitySet::set);
        return affinitySet;
    }

    public void start() {
        for (final Thread thread : this.threads) {
            thread.start();
        }
    }

    public @NonNull TickThreadRunner getCurrentTickThreadRunner() {
        Thread curr = Thread.currentThread();
        for (final TickThreadRunner runner : this.runners) {
            if (runner.thread == curr) {
                return runner;
            }
        }
        throw new IllegalStateException("Couldn't locate current tick thread runner");
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

    private boolean join(final long msToWait, final boolean interruptible) throws InterruptedException {
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
                        if (interruptible) {
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

    private @Nullable ScheduledState poll(final @NonNull TickThreadRunner runner) {
        final long now = System.nanoTime();

        ScheduledState globalHead = globalQueue.peek();
        ScheduledState localHead = runner.localQueue.peek();

        if (globalHead != null && globalHead == localHead) {
            throw new IllegalStateException("Global queue and local queue contain same element");
        } else {
            // handle the local and/or global head being overdue already
            final boolean localOverdue = localHead != null && localHead.isOverdue(now);
            final boolean globalOverdue = globalHead != null && globalHead.isOverdue(now);
            if (localOverdue && globalOverdue) {
                // we should pick the more urgent one
                if (TICK_COMPARATOR_BY_TIME.compare(localHead, globalHead) <= 0) {
                    return runner.localQueue.poll();
                } else return globalQueue.poll();
            }
            else if (localOverdue) {
                return runner.localQueue.poll();
            }
            else if (globalOverdue) {
                return globalQueue.poll();
            }
        }

        ScheduledState best = null;
        boolean bestIsLocal = false;

        if (localHead != null && globalHead != null) {
            if (TICK_COMPARATOR_BY_TIME.compare(localHead, globalHead) <= 0) {
                best = localHead;
                bestIsLocal = true;
            } else {
                best = globalHead;
            }
        } else if (localHead != null) {
            best = localHead;
            bestIsLocal = true;
        } else if (globalHead != null) {
            best = globalHead;
        }

        final TickThreadRunner stealingFrom = runners[nextSteal.getAndUpdate(v -> (v + 1) % runners.length)];

        if (stealingFrom != runner) {
            final ScheduledState stealCandidate = stealingFrom.localQueue.peek();
            if (stealCandidate != null && stealCandidate.isStealable(now)) {
                if (best == null ||
                    TICK_COMPARATOR_BY_TIME.compare(stealCandidate, best) < 0) {
                    return stealingFrom.localQueue.poll();
                }
            }
        }

        if (best != null) {
            return bestIsLocal ? runner.localQueue.poll() : globalQueue.poll();
        }

        return null;
    }

    private void insertFresh(final ScheduledState task) {
        final TickThreadRunner[] runners = this.runners;

        for (final TickThreadRunner runner : runners) {
            // if task is linked, don't insert to queues
            if (runner.linked == task.tick) return;
        }

        // iterate all idle threads, not just the first one
        for (int i = this.idleThreads.nextSetBit(0); i >= 0; i = this.idleThreads.nextSetBit(i + 1)) {
            final TickThreadRunner runner = runners[i];
            if (runner.linked == null) {
                // push to idle thread
                this.idleThreads.clear(i);
                runner.acceptTask(task);
                return;
            }
        }

        // add to queue, will be picked up later
        this.globalQueue.offer(task);
    }

    private ScheduledState returnTask(final TickThreadRunner runner, final ScheduledState reschedule) {
        if (reschedule != null) {
            // we don't wanna send this to the queue if linked
            if (runner.linked == null || runner.linked.state == null || runner.linked.state != reschedule) {
                if (enableWorkStealing) runner.localQueue.offer(reschedule);
                else globalQueue.offer(reschedule);
            }
        }
        // if linked, return that, don't even bother polling
        final ScheduledState ret;
        if (runner.linked != null && runner.linked.state != null) {
            // pinned
            ret = (ScheduledState) runner.linked.state;
        }
        else if (enableWorkStealing) {
            // not pinned, using work stealing
            ret = this.poll(runner);
        }
        else {
            // not work stealing, not pinned, use global
            ret = globalQueue.poll();
        }
        if (ret == null && runner.linked == null) {
            this.idleThreads.set(runner.id);
            final int s = runner.localQueue.size();
            if (s != 0) {
                throw new IllegalStateException("Local queue must be drained before going idle. Currently " + s);
            }
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
        throw new UnsupportedOperationException("Unsupported in AFFINITY Scheduler");
    }

    @Override
    public void notifyTasks(final @NonNull SchedulableTick task) {
        if (task.state == null) return;
        ((ScheduledState) task.state).markedWithTasks.set(true);
    }

    public static final class ScheduledState {
        private final SchedulableTick tick;

        private static final int SCHEDULE_STATE_NOT_SCHEDULED = 0;
        private static final int SCHEDULE_STATE_SCHEDULED = 1;
        private static final int SCHEDULE_STATE_CANCELLED = 2;

        private final AtomicInteger scheduled = new AtomicInteger();
        private AffinitySchedulerThreadPool schedulerOwnedBy;
        private TickThreadRunner ownedBy;
        private final AtomicBoolean markedWithTasks = new AtomicBoolean(false);

        public boolean compareHasTasks() {
            if (markedWithTasks.getAndSet(false)) {
                return true;
            }
            return tick.hasTasks();
        }

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

        // this ignores steal threshold
        public boolean isOverdue(long nanos) {
            return (this.tick.getScheduledStart() - nanos) <= 0L;
        }

        public boolean isStealable(long nanos) {
            return (this.tick.getScheduledStart() - nanos) + schedulerOwnedBy.stealThresh <= 0L;
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

        /**
         * The runner is running mid-tick tasks for one of the tasks that was in its runqueue.
         * <p>
         * stateTarget = the task being ticked
         * </p>
         */
        private static final int STATE_EXECUTING_TASKS = 3;

        public final int id;
        public final AffinitySchedulerThreadPool scheduler;
        private final int affinity;

        private volatile Thread thread;
        private volatile TickThreadRunnerState state = new TickThreadRunnerState(null, STATE_IDLE);
        private static final VarHandle STATE_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "state", TickThreadRunnerState.class);

        private final FastHeapPriorityQueue<ScheduledState> localQueue = new FastHeapPriorityQueue<>(20, TICK_COMPARATOR_BY_TIME, ScheduledState.class);
        private volatile SchedulableTick linked;

        // if swapping, the one we are swapping with must be unscheduled
        public void link(final @NonNull SchedulableTick task, boolean isSwapping) {
            synchronized (scheduler.scheduleLock) {
                if (!scheduler.linkingSupported.getAsBoolean()) {
                    throw new IllegalStateException("Linking not supported in this environment");
                }
                if (task.state != null && ((ScheduledState) task.state).ownedBy != this && !isSwapping) {
                    throw new IllegalStateException("Cannot link task not owned by runner(" + this + "), owned by (" + ((ScheduledState) task.state).ownedBy + ")");
                }
                if (this.linked != null) {
                    throw new IllegalStateException("Runner already linked");
                }

                this.linked = task;

                if (task.state != null && scheduler.idleThreads.get(id)) {
                    scheduler.idleThreads.clear(id);
                    acceptTask((ScheduledState) task.state);
                }
            }
        }

        // must be called during tick or run tasks
        public void unlink() {
            synchronized (scheduler.scheduleLock) {
                this.linked = null;
            }
        }

        @Contract(pure = true)
        public boolean isLinkedTo(final @NonNull SchedulableTick from) {
            return from.state instanceof ScheduledState && from == this.linked;
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

        private record TickThreadRunnerState(ScheduledState stateTarget, int state) {}

        public TickThreadRunner(final int id, final AffinitySchedulerThreadPool scheduler, final int affinity) {
            this.id = id;
            this.scheduler = scheduler;
            this.affinity = affinity;
        }

        public Thread getRunnerThread() {
            return this.thread;
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

        // note: returns false if the expected state isn't the actual current state, true otherwise
        private boolean tryExchangeState(final TickThreadRunnerState expected, final TickThreadRunnerState newState) {
            synchronized (this.scheduler.scheduleLock) {
                if (this.state != expected) {
                    return false;
                }
                this.setStatePlain(newState);
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
            this.thread = Thread.currentThread();
            if (affinity != -1) {
                Affinity.setAffinity(affinity);
                LOGGER.debug("{} bound to CPU {}", this, affinity);
            }

            main_state_loop:
            for (;;) {
                final TickThreadRunnerState startState = this.state;
                final int startStateType = startState.state;
                final ScheduledState startStateTask =  startState.stateTarget;

                if (this.scheduler.halted) {
                    return;
                }

                try {
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
                            // wait until deadline, this will attempt to execute tasks
                            // during the wait to try and be efficient with time
                            if (waitUntilDeadline(startStateTask, startState)) continue main_state_loop;

                            // try exchange state and execute tick
                            if (tryExchangeState(startState, new TickThreadRunnerState(startStateTask, STATE_EXECUTING_TICK))) {
                                final boolean reschedule = startStateTask.tick.runTick();

                                // if runTick throws, then the task is technically orphaned if the exception handler
                                // doesn't work properly, which probably should be how this works, since the tick failed
                                this.returnTask(startStateTask, reschedule);
                            }

                            continue main_state_loop; // finished tick, continue
                        }

                        case STATE_EXECUTING_TICK: {
                            throw new IllegalStateException("Tick execution must be set by runner thread, not by any other thread");
                        }

                        case STATE_EXECUTING_TASKS: {
                            throw new IllegalStateException("Task execution must be set by runner thread, not by any other thread");
                        }

                        default: {
                            throw new IllegalStateException("Unknown state: " + startState);
                        }
                    }
                } catch (Throwable thrown) {
                    scheduler.onException.accept(thrown);
                    return;
                }
            }
        }

        // note: returns true if it should continue state loop
        private boolean waitUntilDeadline(final @NonNull ScheduledState startStateTask, final TickThreadRunnerState startState) {
            final long deadline = startStateTask.tick.getScheduledStart();
            final long adjustedForRunBuffer = deadline - scheduler.runTaskBuff;

            for (;;) {
                if (this.state != startState) {
                    // state was changed unexpectedly(async?)
                    return true;
                }
                // check if we hit the deadline
                final long diff = deadline - System.nanoTime();
                if (diff <= 0L) {
                    break;
                }
                // haven't hit deadline yet, try tasks and then try park
                final TickThreadRunnerState runTasksState = new TickThreadRunnerState(startStateTask, STATE_EXECUTING_TASKS);
                if (
                    scheduler.enableIntermediateTasks &&
                    adjustedForRunBuffer - System.nanoTime() > 0L &&
                    startStateTask.compareHasTasks() &&
                    tryExchangeState(startState, runTasksState)
                ) {
                    // we are in run tasks state
                    startStateTask.tick.runTasks(() -> !scheduler.halted && (adjustedForRunBuffer - System.nanoTime() > 0L));
                    if (!tryExchangeState(runTasksState, startState)) { // restore back to start state
                        throw new IllegalStateException("Couldn't set state back to AWAITING");
                    }
                }
                else {
                    // shouldn't or couldn't run tasks, park until deadline
                    Thread.interrupted();
                    LockSupport.parkNanos(startState, diff);
                    if (this.scheduler.halted) {
                        // just continue to the head of the loop again
                        // we have a check there for if we halted
                        return true;
                    }
                }
            }

            // if the scheduler is halted, return true so it kills the thread
            // immediately instead of trying to run a tick
            return this.scheduler.halted;
        }

        @Override
        public String toString() {
            return "AffinityRunner#" + id;
        }
    }
}
