package io.canvasmc.canvas.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import io.canvasmc.canvas.util.ThreadBuilder;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheChunkSystem extends PrioritisedThreadPool {

    private final Logger LOGGER;

    private final TheChunkSystem.COWArrayList<TheChunkSystem.ExecutorGroup> executors = new TheChunkSystem.COWArrayList<>(TheChunkSystem.ExecutorGroup.class);
    private final TheChunkSystem.COWArrayList<TheChunkSystem.PrioritisedThread> threads = new TheChunkSystem.COWArrayList<>(TheChunkSystem.PrioritisedThread.class);
    private final TheChunkSystem.COWArrayList<TheChunkSystem.PrioritisedThread> aliveThreads = new TheChunkSystem.COWArrayList<>(TheChunkSystem.PrioritisedThread.class);

    private static final Priority HIGH_PRIORITY_NOTIFY_THRESHOLD = Priority.HIGH;
    private static final Priority QUEUE_SHUTDOWN_PRIORITY = Priority.HIGH;

    private boolean shutdown;

    public TheChunkSystem(final int workerThreadCount, final ThreadBuilder threadInitializer, final String name) {
        super(threadInitializer);
        LOGGER = LoggerFactory.getLogger("TheChunkSystem/" + name);
        LOGGER.info("Initialized new LS ChunkSystem '{}' with {} allocated threads", name, workerThreadCount);
        this.adjustThreadCount(workerThreadCount);
    }

    public Thread[] getAliveThreads() {
        final TheChunkSystem.PrioritisedThread[] threads = this.aliveThreads.getArray();

        return Arrays.copyOf(threads, threads.length, Thread[].class);
    }

    public Thread[] getCoreThreads() {
        final TheChunkSystem.PrioritisedThread[] threads = this.threads.getArray();

        return Arrays.copyOf(threads, threads.length, Thread[].class);
    }

    /**
     * Prevents creation of new queues, shutdowns all non-shutdown queues if specified
     */
    public void halt(final boolean shutdownQueues) {
        synchronized (this) {
            this.shutdown = true;
        }

        if (shutdownQueues) {
            for (final TheChunkSystem.ExecutorGroup group : this.executors.getArray()) {
                for (final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor : group.executors.getArray()) {
                    executor.shutdown();
                }
            }
        }

        for (final TheChunkSystem.PrioritisedThread thread : this.threads.getArray()) {
            thread.halt(false);
        }
    }

    /**
     * Waits until all threads in this pool have shutdown, or until the specified time has passed.
     * @param msToWait Maximum time to wait.
     * @return {@code false} if the maximum time passed, {@code true} otherwise.
     */
    public boolean join(final long msToWait) {
        try {
            return this.join(msToWait, false);
        } catch (final InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Waits until all threads in this pool have shutdown, or until the specified time has passed.
     * @param msToWait Maximum time to wait.
     * @return {@code false} if the maximum time passed, {@code true} otherwise.
     * @throws InterruptedException If this thread is interrupted.
     */
    public boolean joinInterruptable(final long msToWait) throws InterruptedException {
        return this.join(msToWait, true);
    }

    /**
     * Shuts down this thread pool, optionally waiting for all tasks to be executed.
     * This function will invoke {@link PrioritisedExecutor#shutdown()} on all created executors on this
     * thread pool.
     * @param wait Whether to wait for tasks to be executed
     */
    public void shutdown(final boolean wait) {
        synchronized (this) {
            this.shutdown = true;
        }

        for (final TheChunkSystem.ExecutorGroup group : this.executors.getArray()) {
            for (final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor : group.executors.getArray()) {
                executor.shutdown();
            }
        }


        for (final TheChunkSystem.PrioritisedThread thread : this.threads.getArray()) {
            // none of these can be true or else NPE
            thread.close(false, false);
        }

        if (wait) {
            this.join(0L);
        }
    }

    private void die(final TheChunkSystem.PrioritisedThread thread) {
        this.aliveThreads.remove(thread);
    }

    public void adjustThreadCount(final int threads) {
        synchronized (this) {
            if (this.shutdown) {
                return;
            }

            final TheChunkSystem.PrioritisedThread[] currentThreads = this.threads.getArray();
            if (threads == currentThreads.length) {
                // no adjustment needed
                return;
            }

            if (threads < currentThreads.length) {
                // we need to trim threads
                for (int i = 0, difference = currentThreads.length - threads; i < difference; ++i) {
                    final TheChunkSystem.PrioritisedThread remove = currentThreads[currentThreads.length - i - 1];

                    remove.halt(false);
                    this.threads.remove(remove);
                }
            } else {
                // we need to add threads
                for (int i = 0, difference = threads - currentThreads.length; i < difference; ++i) {
                    final TheChunkSystem.PrioritisedThread thread = new TheChunkSystem.PrioritisedThread();

                    this.threadModifier.accept(thread);
                    this.aliveThreads.add(thread);
                    this.threads.add(thread);

                    thread.start();
                }
            }
        }
    }

    private static int compareInsideGroup(final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor src, final Priority srcPriority,
                                          final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor dst, final Priority dstPriority) {
        final int priorityCompare = srcPriority.ordinal() - dstPriority.ordinal();
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        return TimeUtil.compareTimes(src.lastRetrieved, dst.lastRetrieved);
    }

    private static int compareOutsideGroup(final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor src, final Priority srcPriority,
                                           final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor dst, final Priority dstPriority) {
        return TimeUtil.compareTimes(src.lastRetrieved, dst.lastRetrieved);
    }

    private TheChunkSystem.ExecutorGroup.ThreadPoolExecutor obtainQueue() {
        final long time = System.nanoTime();
        synchronized (this) {
            TheChunkSystem.ExecutorGroup.ThreadPoolExecutor ret = null;
            Priority retPriority = null;

            for (final TheChunkSystem.ExecutorGroup executorGroup : this.executors.getArray()) {
                TheChunkSystem.ExecutorGroup.ThreadPoolExecutor highest = null;
                Priority highestPriority = null;
                for (final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor : executorGroup.executors.getArray()) {
                    final Priority priority = executor.getTargetPriority();

                    if (priority == null) {
                        continue;
                    }

                    if (highestPriority == null || compareInsideGroup(highest, highestPriority, executor, priority) > 0) {
                        highest = executor;
                        highestPriority = priority;
                    }
                }

                if (highest == null) {
                    continue;
                }

                if (ret == null || compareOutsideGroup(ret, retPriority, highest, highestPriority) > 0) {
                    ret = highest;
                    retPriority = highestPriority;
                }
            }

            if (ret != null) {
                ret.lastRetrieved = time;
                return ret;
            }

            return ret;
        }
    }

    private void returnQueue(final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor) {
        if (executor.isShutdown() && executor.queue.hasNoScheduledTasks()) {
            executor.getGroup().executors.remove(executor);
        }
    }

    private void notifyAllThreads() {
        for (final TheChunkSystem.PrioritisedThread thread : this.threads.getArray()) {
            thread.notifyTasks();
        }
    }

    public TheChunkSystem.ExecutorGroup createExecutorGroup() {
        synchronized (this) {
            if (this.shutdown) {
                throw new IllegalStateException("Queue is shutdown: " + this.toString());
            }

            final TheChunkSystem.ExecutorGroup ret = new TheChunkSystem.ExecutorGroup();

            this.executors.add(ret);

            return ret;
        }
    }

    private final class PrioritisedThread extends PrioritisedQueueExecutorThread {

        private final AtomicBoolean alertedHighPriority = new AtomicBoolean();

        public PrioritisedThread() {
            super(null);
        }

        public boolean alertHighPriorityExecutor() {
            if (!this.notifyTasks()) {
                if (!this.alertedHighPriority.get()) {
                    this.alertedHighPriority.set(true);
                }
                return false;
            }

            return true;
        }

        private boolean isAlertedHighPriority() {
            return this.alertedHighPriority.get() && this.alertedHighPriority.getAndSet(false);
        }

        @Override
        protected void die() {
            TheChunkSystem.this.die(this);
        }

        @Override
        protected boolean pollTasks() {
            boolean ret = false;

            for (;;) {
                if (this.halted) {
                    break;
                }

                final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor = TheChunkSystem.this.obtainQueue();
                if (executor == null) {
                    break;
                }
                final long deadline = System.nanoTime() + executor.queueMaxHoldTime;
                do {
                    try {
                        if (this.halted || executor.halt) {
                            break;
                        }
                        if (!executor.executeTask()) {
                            // no more tasks, try next queue
                            break;
                        }
                        ret = true;
                    } catch (final Throwable throwable) {
                        LOGGER.error("Exception thrown from thread '" + this.getName() + "' in queue '" + executor.toString() + "'", throwable);
                    }
                } while (!this.isAlertedHighPriority() && System.nanoTime() <= deadline);

                TheChunkSystem.this.returnQueue(executor);
            }


            return ret;
        }
    }

    public final class ExecutorGroup {

        private final AtomicLong subOrderGenerator = new AtomicLong();
        private final TheChunkSystem.COWArrayList<TheChunkSystem.ExecutorGroup.ThreadPoolExecutor> executors = new TheChunkSystem.COWArrayList<>(TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.class);

        private ExecutorGroup() {
        }

        public TheChunkSystem.ExecutorGroup.ThreadPoolExecutor[] getAllExecutors() {
            return this.executors.getArray().clone();
        }

        private TheChunkSystem getThreadPool() {
            return TheChunkSystem.this;
        }

        public TheChunkSystem.ExecutorGroup.ThreadPoolExecutor createExecutor(final long queueMaxHoldTime, final int flags) {
            synchronized (TheChunkSystem.this) {
                if (TheChunkSystem.this.shutdown) {
                    throw new IllegalStateException("Queue is shutdown: " + TheChunkSystem.this.toString());
                }

                final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor ret = new TheChunkSystem.ExecutorGroup.ThreadPoolExecutor(queueMaxHoldTime, flags);

                this.executors.add(ret);

                return ret;
            }
        }

        public final class ThreadPoolExecutor implements PrioritisedExecutor {

            private final PrioritisedTaskQueue queue = new PrioritisedTaskQueue();

            private final long queueMaxHoldTime;
            private volatile boolean halt;
            private long lastRetrieved = System.nanoTime();

            private ThreadPoolExecutor(final long queueMaxHoldTime, final int flags) {
                this.queueMaxHoldTime = queueMaxHoldTime;
            }

            private TheChunkSystem.ExecutorGroup getGroup() {
                return TheChunkSystem.ExecutorGroup.this;
            }

            private boolean canNotify() {
                return !this.halt;
            }

            private void notifyHighPriority() {
                if (!this.canNotify()) {
                    return;
                }
                for (final TheChunkSystem.PrioritisedThread thread : this.getGroup().getThreadPool().threads.getArray()) {
                    if (thread.alertHighPriorityExecutor()) {
                        return;
                    }
                }
            }

            private void notifyScheduled() {
                if (!this.canNotify()) {
                    return;
                }
                for (final TheChunkSystem.PrioritisedThread thread : this.getGroup().getThreadPool().threads.getArray()) {
                    if (thread.notifyTasks()) {
                        return;
                    }
                }
            }

            /**
             * Removes this queue from the thread pool without shutting the queue down or waiting for queued tasks to be executed
             */
            public void halt() {
                this.halt = true;

                TheChunkSystem.ExecutorGroup.this.executors.remove(this);
            }

            /**
             * Returns whether this executor is scheduled to run tasks or is running tasks, otherwise it returns whether
             * this queue is not halted and not shutdown.
             */
            public boolean isActive() {
                if (this.halt) {
                    return false;
                } else {
                    if (!this.isShutdown()) {
                        return true;
                    }

                    return !this.queue.hasNoScheduledTasks();
                }
            }

            @Override
            public boolean shutdown() {
                if (!this.queue.shutdown()) {
                    return false;
                }

                if (this.queue.hasNoScheduledTasks()) {
                    TheChunkSystem.ExecutorGroup.this.executors.remove(this);
                }

                return true;
            }

            @Override
            public boolean isShutdown() {
                return this.queue.isShutdown();
            }

            Priority getTargetPriority() {
                final Priority ret = this.queue.getHighestPriority();
                if (!this.isShutdown()) {
                    return ret;
                }

                return ret == null ? QUEUE_SHUTDOWN_PRIORITY : Priority.max(ret, QUEUE_SHUTDOWN_PRIORITY);
            }

            @Override
            public long getTotalTasksScheduled() {
                return this.queue.getTotalTasksScheduled();
            }

            @Override
            public long getTotalTasksExecuted() {
                return this.queue.getTotalTasksExecuted();
            }

            @Override
            public long generateNextSubOrder() {
                return TheChunkSystem.ExecutorGroup.this.subOrderGenerator.getAndIncrement();
            }

            @Override
            public boolean executeTask() {
                return this.queue.executeTask();
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
            public PrioritisedTask createTask(final Runnable task) {
                return this.createTask(task, Priority.NORMAL);
            }

            @Override
            public PrioritisedTask createTask(final Runnable task, final Priority priority) {
                return this.createTask(task, priority, this.generateNextSubOrder());
            }

            @Override
            public PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder) {
                return new TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.WrappedTask(this.queue.createTask(task, priority, subOrder));
            }

            private final class WrappedTask implements PrioritisedTask {

                private final PrioritisedTask wrapped;

                private WrappedTask(final PrioritisedTask wrapped) {
                    this.wrapped = wrapped;
                }

                @Override
                public PrioritisedExecutor getExecutor() {
                    return TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this;
                }

                @Override
                public boolean queue() {
                    if (this.wrapped.queue()) {
                        final Priority priority = this.getPriority();
                        if (priority != Priority.COMPLETING) {
                            if (priority.isHigherOrEqualPriority(HIGH_PRIORITY_NOTIFY_THRESHOLD)) {
                                TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyHighPriority();
                            } else {
                                TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyScheduled();
                            }
                        }
                        return true;
                    }

                    return false;
                }

                @Override
                public boolean isQueued() {
                    return this.wrapped.isQueued();
                }

                @Override
                public boolean cancel() {
                    return this.wrapped.cancel();
                }

                @Override
                public boolean execute() {
                    return this.wrapped.execute();
                }

                @Override
                public Priority getPriority() {
                    return this.wrapped.getPriority();
                }

                @Override
                public boolean setPriority(final Priority priority) {
                    if (this.wrapped.setPriority(priority)) {
                        if (priority.isHigherOrEqualPriority(HIGH_PRIORITY_NOTIFY_THRESHOLD)) {
                            TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyHighPriority();
                        }
                        return true;
                    }

                    return false;
                }

                @Override
                public boolean raisePriority(final Priority priority) {
                    if (this.wrapped.raisePriority(priority)) {
                        if (priority.isHigherOrEqualPriority(HIGH_PRIORITY_NOTIFY_THRESHOLD)) {
                            TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyHighPriority();
                        }
                        return true;
                    }

                    return false;
                }

                @Override
                public boolean lowerPriority(final Priority priority) {
                    return this.wrapped.lowerPriority(priority);
                }

                @Override
                public long getSubOrder() {
                    return this.wrapped.getSubOrder();
                }

                @Override
                public boolean setSubOrder(final long subOrder) {
                    return this.wrapped.setSubOrder(subOrder);
                }

                @Override
                public boolean raiseSubOrder(final long subOrder) {
                    return this.wrapped.raiseSubOrder(subOrder);
                }

                @Override
                public boolean lowerSubOrder(final long subOrder) {
                    return this.wrapped.lowerSubOrder(subOrder);
                }

                @Override
                public boolean setPriorityAndSubOrder(final Priority priority, final long subOrder) {
                    if (this.wrapped.setPriorityAndSubOrder(priority, subOrder)) {
                        if (priority.isHigherOrEqualPriority(HIGH_PRIORITY_NOTIFY_THRESHOLD)) {
                            TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyHighPriority();
                        }
                        return true;
                    }

                    return false;
                }
            }
        }
    }

    private static final class COWArrayList<E> {

        private volatile E[] array;

        public COWArrayList(final Class<E> clazz) {
            this.array = (E[])Array.newInstance(clazz, 0);
        }

        public E[] getArray() {
            return this.array;
        }

        public void add(final E element) {
            synchronized (this) {
                final E[] array = this.array;

                final E[] copy = Arrays.copyOf(array, array.length + 1);
                copy[array.length] = element;

                this.array = copy;
            }
        }

        public boolean remove(final E element) {
            synchronized (this) {
                final E[] array = this.array;
                int index = -1;
                for (int i = 0, len = array.length; i < len; ++i) {
                    if (array[i] == element) {
                        index = i;
                        break;
                    }
                }

                if (index == -1) {
                    return false;
                }

                final E[] copy = (E[])Array.newInstance(array.getClass().getComponentType(), array.length - 1);

                System.arraycopy(array, 0, copy, 0, index);
                System.arraycopy(array, index + 1, copy, index, (array.length - 1) - index);

                this.array = copy;
            }

            return true;
        }
    }
}
