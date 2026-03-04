package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.set.LinkedSortedSet;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.util.CpuTopology;
import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static io.canvasmc.canvas.Config.LOGGER;

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

    private static final Comparator<ScheduledState> TICK_COMPARATOR_BY_TIME = (final ScheduledState s1, final ScheduledState s2) -> {
        final SchedulableTick t1 = s1.tick;
        final SchedulableTick t2 = s2.tick;

        final int timeCompare = TimeUtil.compareTimes(t1.scheduledStart, t2.scheduledStart);
        if (timeCompare != 0) {
            return timeCompare;
        }

        // compare these if the start time comparison returns 0
        return Long.compare(t1.id, t2.id);
    };

    private final TickThreadRunner[] runners;
    private final Thread[] threads;
    private final LinkedSortedSet<ScheduledState> awaiting = new LinkedSortedSet<>(TICK_COMPARATOR_BY_TIME);
    private final TickPriorityQueue<ScheduledState> queued = new TickPriorityQueue<>(100, TICK_COMPARATOR_BY_TIME, ScheduledState.class);
    private final BitSet idleThreads;

    private final Object scheduleLock = new Object();
    private final long runTaskBuff;
    private final BooleanSupplier linkingSupported;

    private volatile boolean halted;

    public AffinitySchedulerThreadPool(final int threads, final ThreadFactory threadFactory, long runTaskBuff, BooleanSupplier linkingSupported) {
        this.runTaskBuff = runTaskBuff;
        this.linkingSupported = linkingSupported;
        final BitSet idleThreads = new BitSet(threads);
        for (int i = 0; i < threads; ++i) {
            idleThreads.set(i);
        }
        this.idleThreads = idleThreads;

        final BitSet affinitySet;
        if (Config.INSTANCE.scheduler.enableAffinitySchedulerCpuAffinity) {
            if (!CpuTopology.isLinux()) {
                LOGGER.warn("Affinity setting is only supported on Linux");
                affinitySet = new BitSet();
            }
            else {
                affinitySet = getAffinity(Config.INSTANCE.scheduler.tickRegionAffinity);
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

    private @NonNull BitSet getAffinity(@NonNull List<String> affinity) {
        if (affinity.isEmpty()) {
            LOGGER.warn("No affinity set configured, backing off, logging CPU topology:\n{}", CpuTopology.compileOutput());
            return new BitSet();
        }
        int maxAvailable = Runtime.getRuntime().availableProcessors();
        BitSet affinitySet = new BitSet(affinity.size());
        affinity.stream()
            .mapToInt(str -> {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException ignored) {
                    LOGGER.error("Unable to parse cpu id {} to a valid number, falling back to 0.", str);
                    return -1;
                }
            })
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

    public BooleanSupplier getLinkingSupported() {
        return linkingSupported;
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

    private void insertFresh(final ScheduledState task) {
        final TickThreadRunner[] runners = this.runners;

        final int firstIdleThread = this.idleThreads.nextSetBit(0);

        if (firstIdleThread != -1) {
            final TickThreadRunner runner = runners[firstIdleThread];
            if (runner.linked == null) {
                // push to idle thread
                this.idleThreads.clear(firstIdleThread);
                task.awaitingLink = this.awaiting.addLast(task);
                runner.acceptTask(task);
                return;
            }
        }

        // add to queue, will be picked up later
        this.queued.offer(task);
    }

    private void takeTask(final @NonNull ScheduledState tick) {
        if (!this.awaiting.remove(tick.awaitingLink)) {
            throw new IllegalStateException("Task is not in awaiting");
        }
        tick.awaitingLink = null;
    }

    private ScheduledState returnTask(final TickThreadRunner runner, final ScheduledState reschedule) {
        if (reschedule != null) {
            // we don't wanna send this to the queue if linked
            if (runner.linked == null || runner.linked != reschedule) {
                this.queued.offer(reschedule);
            }
        }
        // if linked, return that, don't even bother polling
        final ScheduledState ret = runner.linked != null ? runner.linked : this.queued.poll();
        if (ret == null && runner.linked == null) {
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
        throw new UnsupportedOperationException("Unsupported in AFFINITY Scheduler");
    }

    @Override
    public void notifyTasks(final @NonNull SchedulableTick task) {
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
            if (markedWithTasks.get()) {
                markedWithTasks.set(false);
                return true;
            }
            return tick.hasTasks();
        }

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

        public final int id;
        public final AffinitySchedulerThreadPool scheduler;
        private final int affinity;

        private volatile Thread thread;
        private volatile TickThreadRunnerState state = new TickThreadRunnerState(null, STATE_IDLE);
        private static final VarHandle STATE_HANDLE = ConcurrentUtil.getVarHandle(TickThreadRunner.class, "state", TickThreadRunnerState.class);

        private volatile ScheduledState linked;

        // if swapping, the one we are swapping with must be unscheduled
        public void link(final @NonNull ScheduledState task, boolean isSwapping) {
            synchronized (scheduler.scheduleLock) {
                if (!scheduler.linkingSupported.getAsBoolean()) {
                    throw new IllegalStateException("Linking not supported in this environment");
                }
                if (task.ownedBy != this && !isSwapping) {
                    throw new IllegalStateException("Cannot link task not owned by runner(" + this + "), owned by (" + task.ownedBy + ")");
                }
                if (task.awaitingLink != null || scheduler.queued.contains(task)) {
                    throw new IllegalStateException("Cannot link queued/awaiting task");
                }
                if (this.linked != null) {
                    throw new IllegalStateException("Runner already linked");
                }

                this.linked = task;

                if (scheduler.idleThreads.get(id)) {
                    scheduler.idleThreads.clear(id);
                    task.awaitingLink = scheduler.awaiting.addLast(task);
                    acceptTask(task);
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
            return from.state instanceof ScheduledState scheduledState && scheduledState == this.linked;
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

        private static record TickThreadRunnerState(ScheduledState stateTarget, int state) {}

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

        private void replaceTask(final ScheduledState task) {
            final TickThreadRunnerState state = this.state;
            if (state.state != STATE_AWAITING_TICK) {
                throw new IllegalStateException("Cannot replace task in state " + state);
            }
            if (task.ownedBy != null) {
                throw new IllegalStateException("Already owned by another runner");
            }
            if (this.linked != null) {
                throw new IllegalStateException("Cannot replace task with linked runner");
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
                this.scheduler.takeTask(task);
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
            return "AffinityRunner#" + id;
        }
    }
}
