package io.canvasmc.canvas.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrioritisedQueueExecutorThread extends Thread implements PrioritisedExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrioritisedQueueExecutorThread.class);

    protected final PrioritisedExecutor queue;

    protected volatile boolean threadShutdown;

    protected volatile boolean threadParked;
    protected static final VarHandle THREAD_PARKED_HANDLE = ConcurrentUtil.getVarHandle(PrioritisedQueueExecutorThread.class, "threadParked", boolean.class);

    protected volatile boolean halted;

    public PrioritisedQueueExecutorThread(final PrioritisedExecutor queue) {
        this.queue = queue;
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

    public final void doRun() {

        main_loop:
        for (;;) {
            this.pollTasks();

            for (;;) {
                if (this.pollTasks()) {
                    // restart loop, found tasks
                    continue main_loop;
                }

                if (this.handleClose()) {
                    return; // we're done
                }

                break;
            }

            if (this.handleClose()) {
                return;
            }

            this.setThreadParkedVolatile(true);

            // We need to parse here to avoid a race condition where a thread queues a task before we set parked to true
            // (i.e. it will not notify us)
            if (this.pollTasks()) {
                this.setThreadParkedVolatile(false);
                continue;
            }

            if (this.handleClose()) {
                return;
            }

            // we don't need to check parked before sleeping, but we do need to check parked in a do-while loop
            // LockSupport.park() can fail for any reason
            while (this.getThreadParkedVolatile()) {
                Thread.interrupted();
                LockSupport.park("Waiting on tasks");
            }
        }
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
                break;
            }
            try {
                if (!this.queue.executeTask()) {
                    break;
                }
                ret = true;
            } catch (final Throwable throwable) {
                LOGGER.error("Exception thrown from prioritized runnable task in thread '" + this.getName() + "'", throwable);
            }
        }

        return ret;
    }

    protected boolean handleClose() {
        if (this.threadShutdown) {
            this.pollTasks(); // this ensures we've emptied the queue
            return true;
        }
        return false;
    }

    /**
     * Notify this thread that a task has been added to its queue
     * @return {@code true} if this thread was waiting for tasks, {@code false} if it is executing tasks
     */
    public boolean notifyTasks() {
        if (this.getThreadParkedVolatile() && this.exchangeThreadParkedVolatile(false)) {
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
    public PrioritisedTask queueTask(final Runnable task, final Priority priority, final long subOrder) {
        final PrioritisedTask ret = this.createTask(task, priority, subOrder);

        ret.queue();

        return ret;
    }


    @Override
    public PrioritisedTask createTask(Runnable task) {
        final PrioritisedTask queueTask = this.queue.createTask(task);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority) {
        final PrioritisedTask queueTask = this.queue.createTask(task, priority);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder) {
        final PrioritisedTask queueTask = this.queue.createTask(task, priority, subOrder);

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
        this.setThreadParkedVolatile(false);
        LockSupport.unpark(this);

        if (wait) {
            boolean interrupted = false;
            for (;;) {
                if (this.isAlive()) {
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
        this.setThreadParkedVolatile(false);
        LockSupport.unpark(this);
    }

    protected final boolean getThreadParkedVolatile() {
        return (boolean)THREAD_PARKED_HANDLE.getVolatile(this);
    }

    protected final boolean exchangeThreadParkedVolatile(final boolean value) {
        return (boolean)THREAD_PARKED_HANDLE.getAndSet(this, value);
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
        public boolean setPriorityAndSubOrder(final Priority priority, final long subOrder) {
            return this.queueTask.setPriorityAndSubOrder(priority, subOrder);
        }
    }
}
