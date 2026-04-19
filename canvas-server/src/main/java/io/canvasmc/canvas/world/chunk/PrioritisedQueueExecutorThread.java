package io.canvasmc.canvas.world.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

/**
 * Thread which will continuously drain from a specified queue.
 * <p>
 *     Note: When using this thread, queue additions to the underlying {@link #queue} are not sufficient to get this thread
 *     to execute the task. The function {@link #notifyTasks()} must be used after scheduling a task. For expected behaviour
 *     of task scheduling, use the methods provided on this class to schedule tasks.
 * </p>
 */
public class PrioritisedQueueExecutorThread extends Thread implements PrioritisedExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrioritisedQueueExecutorThread.class);

    protected final PrioritisedExecutor queue;

    protected volatile boolean threadShutdown;

    protected volatile boolean threadParked;
    protected static final VarHandle THREAD_PARKED_HANDLE = ConcurrentUtil.getVarHandle(PrioritisedQueueExecutorThread.class, "threadParked", boolean.class);

    protected volatile boolean halted;

    protected final long spinWaitTimeNS;

    protected static final long DEFAULT_SPINWAIT_TIME = (long)(0.1e6); // 0.1ms

    public PrioritisedQueueExecutorThread(final PrioritisedExecutor queue) {
        this(queue, DEFAULT_SPINWAIT_TIME);
    }

    public PrioritisedQueueExecutorThread(final PrioritisedExecutor queue, final long spinWaitTimeNS) { // in ns
        this.queue = queue;
        this.spinWaitTimeNS = spinWaitTimeNS;
    }

    @Override
    public final void run() {
        try {
            this.begin();
            this.doRun();
        } finally {
            this.die();
        }
    }

    // rets whether there are more tasks
    private boolean mainLoop() {
        this.pollTasks();

        final long spinWaitTimeNS = this.spinWaitTimeNS;
        // spinwait if configured
        if (spinWaitTimeNS > 0L) {
            final long start = System.nanoTime();
            final long deadline = start + spinWaitTimeNS;
            for (long sleepTime = Math.min(100_000L, spinWaitTimeNS);;) {
                this.setParked();

                if (this.pollTasks()) {
                    this.unsetParked();
                    return true;
                }

                if (this.handleClose()) {
                    this.unsetParked();
                    return false;
                }

                this.unsetParked();

                if (this.pollTasks()) {
                    return true;
                }

                final long timeToDeadline = deadline - System.nanoTime();

                if (timeToDeadline <= 0L) {
                    // begin long parking
                    break;
                }

                // don't try to sleep past the spinwait deadline
                sleepTime = Math.min(timeToDeadline, sleepTime);
            }
        } // else: go straight to long parking

        this.setParked();

        // We need to parse here to avoid a race condition where a thread queues a task before we set parked to true
        // (i.e. it will not notify us)
        if (this.pollTasks()) {
            this.unsetParked();
            return true;
        }

        if (this.handleClose()) {
            this.unsetParked();
            return false;
        }

        // we don't need to check parked before sleeping, but we do need to check parked in a do-while loop
        // LockSupport.park() can fail for any reason
        while (this.getThreadParkedVolatile()) {
            Thread.interrupted();
            LockSupport.park("Long parking");
        }

        return true;
    }

    private void doRun() {
        while (this.mainLoop());
    }

    protected void begin() {}

    protected void die() {}

    /**
     * Attempts to poll as many tasks as possible, returning when finished.
     * @return Whether any tasks were executed.
     */
    protected boolean pollTasks() {
        boolean ret = false;

        for (;;) {
            if (this.halted) {
                return ret;
            }
            try {
                if (!this.queue.executeTask()) {
                    return ret;
                }
                ret = true;
            } catch (final Throwable throwable) {
                LOGGER.error("Exception thrown from prioritized runnable task in thread '" + this.getName() + "'", throwable);
            }
        }
    }

    protected final boolean handleClose() {
        if (this.threadShutdown) {
            this.pollTasks(); // this ensures we've emptied the queue
            return true;
        }
        return false;
    }

    private boolean unsetParked() {
        // avoid contending the cache (i.e. forcing write or exclusive ownership) by doing no writes unless we need to
        return this.getThreadParkedVolatile() && this.compareAndExchangeThreadParkedVolatile(true, false);
    }

    private void setParked() {
        this.setThreadParkedVolatile(true);
    }

    /**
     * Notify this thread that a task has been added to its queue
     * @return {@code true} if this thread was waiting for tasks, {@code false} if it is executing tasks
     */
    public final boolean notifyTasks() {
        if (this.unsetParked()) {
            LockSupport.unpark(this);
            return true;
        }
        return false;
    }

    @Override
    public long getTotalTasksExecuted() {
        return this.queue.getTotalTasksExecuted();
    }

    @Override
    public long getTotalTasksScheduled() {
        return this.queue.getTotalTasksScheduled();
    }

    @Override
    public long generateNextSubOrder() {
        return this.queue.generateNextSubOrder();
    }

    @Override
    public boolean shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    /**
     * {@inheritDoc}
     * @throws IllegalStateException Always
     */
    @Override
    public boolean executeTask() throws IllegalStateException {
        throw new IllegalStateException();
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task) {
        final PrioritisedTask ret = this.createTask(task);

        ret.queue();

        return ret;
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task, final Priority priority) {
        final PrioritisedTask ret = this.createTask(task, priority);

        ret.queue();

        return ret;
    }

    @Override
    public PrioritisedTask queueTask(final Runnable task, final Priority priority, final long subOrder,
                                     final long stream) {
        final PrioritisedTask ret = this.createTask(task, priority, subOrder, stream);

        ret.queue();

        return ret;
    }


    @Override
    public PrioritisedTask createTask(final Runnable task) {
        final PrioritisedTask queueTask = this.queue.createTask(task);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority) {
        final PrioritisedTask queueTask = this.queue.createTask(task, priority);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder,
                                      final long stream) {
        final PrioritisedTask queueTask = this.queue.createTask(task, priority, subOrder, stream);

        return new WrappedTask(queueTask);
    }

    /**
     * Closes this queue executor's queue. Optionally waits for all tasks in queue to be executed if {@code wait} is true.
     * <p>
     *     This function is MT-Safe.
     * </p>
     * @param wait If this call is to wait until this thread shuts down.
     * @param killQueue Whether to shutdown this thread's queue
     * @return whether this thread shut down the queue
     * @see #halt(boolean)
     */
    public boolean close(final boolean wait, final boolean killQueue) {
        final boolean ret = killQueue && this.queue.shutdown();
        this.threadShutdown = true;

        // force thread to respond to the shutdown
        this.notifyTasks();

        if (wait) {
            boolean interrupted = false;
            for (;;) {
                if (!this.isAlive()) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                try {
                    this.join();
                } catch (final InterruptedException ex) {
                    interrupted = true;
                }
            }
        }

        return ret;
    }


    /**
     * Causes this thread to exit without draining the queue. To ensure tasks are completed, use {@link #close(boolean, boolean)}.
     * <p>
     *     This is not safe to call with {@link #close(boolean, boolean)} if <code>wait = true</code>, in which case
     *     the waiting thread may block indefinitely.
     * </p>
     * <p>
     *     This function is MT-Safe.
     * </p>
     * @param killQueue Whether to shutdown this thread's queue
     * @see #close(boolean, boolean)
     */
    public void halt(final boolean killQueue) {
        if (killQueue) {
            this.queue.shutdown();
        }
        this.threadShutdown = true;
        this.halted = true;

        // force thread to respond to the shutdown
        this.notifyTasks();
    }

    protected final boolean getThreadParkedVolatile() {
        return (boolean)THREAD_PARKED_HANDLE.getVolatile(this);
    }

    protected final boolean compareAndExchangeThreadParkedVolatile(final boolean expect, final boolean update) {
        return (boolean)THREAD_PARKED_HANDLE.compareAndExchange(this, expect, update);
    }

    protected final void setThreadParkedVolatile(final boolean value) {
        THREAD_PARKED_HANDLE.setVolatile(this, value);
    }

    /**
     * Required so that queue() can notify (unpark) this thread
     */
    private final class WrappedTask implements PrioritisedTask {
        private final PrioritisedTask queueTask;

        public WrappedTask(final PrioritisedTask queueTask) {
            this.queueTask = queueTask;
        }

        @Override
        public PrioritisedExecutor getExecutor() {
            return PrioritisedQueueExecutorThread.this;
        }

        @Override
        public boolean queue() {
            final boolean ret = this.queueTask.queue();
            if (ret) {
                PrioritisedQueueExecutorThread.this.notifyTasks();
            }
            return ret;
        }

        @Override
        public boolean isQueued() {
            return this.queueTask.isQueued();
        }

        @Override
        public boolean cancel() {
            return this.queueTask.cancel();
        }

        @Override
        public boolean execute() {
            return this.queueTask.execute();
        }

        @Override
        public Priority getPriority() {
            return this.queueTask.getPriority();
        }

        @Override
        public boolean setPriority(final Priority priority) {
            return this.queueTask.setPriority(priority);
        }

        @Override
        public boolean raisePriority(final Priority priority) {
            return this.queueTask.raisePriority(priority);
        }

        @Override
        public boolean lowerPriority(final Priority priority) {
            return this.queueTask.lowerPriority(priority);
        }

        @Override
        public long getSubOrder() {
            return this.queueTask.getSubOrder();
        }

        @Override
        public boolean setSubOrder(final long subOrder) {
            return this.queueTask.setSubOrder(subOrder);
        }

        @Override
        public boolean raiseSubOrder(final long subOrder) {
            return this.queueTask.raiseSubOrder(subOrder);
        }

        @Override
        public boolean lowerSubOrder(final long subOrder) {
            return this.queueTask.lowerSubOrder(subOrder);
        }

        @Override
        public long getStream() {
            return this.queueTask.getStream();
        }

        @Override
        public boolean setStream(final long stream) {
            return this.queueTask.setStream(stream);
        }

        @Override
        public boolean setPrioritySubOrderStream(final Priority priority, final long subOrder, final long stream) {
            return this.queueTask.setPrioritySubOrderStream(priority, subOrder, stream);
        }

        @Override
        public PriorityState getPriorityState() {
            return this.queueTask.getPriorityState();
        }
    }
}
