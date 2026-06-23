package io.canvasmc.canvas.world.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.QueueExecutorRunnable;
import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.concurrentutil.executor.thread.BalancedPrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.list.COWArrayList;
import ca.spottedleaf.concurrentutil.util.LazyRunnable;
import ca.spottedleaf.concurrentutil.util.Priority;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is based off {@link BalancedPrioritisedThreadPool} from ConcurrentUtil by SpottedLeaf. This code has been
 * modified from its original form to remove max parallelism and rework the constructor and such for CanvasMC.
 *
 * @author dueris modifier
 * @author spottedleaf original author
 */
public final class BalancedChunkSystem extends BalancedPrioritisedThreadPool {

    private final Logger LOGGER;

    private final COWArrayList<WorkerThread> threads = new COWArrayList<>(WorkerThread.class);
    private final COWArrayList<WorkerThread> aliveThreads = new COWArrayList<>(WorkerThread.class);
    private final BalancedChunkSystem.OrderedStreamGroup stream = new OrderedStreamGroup(new AtomicLong());

    private boolean shutdown;

    public BalancedChunkSystem(final long groupTimeSliceNS, final int workerThreadCount, final ThreadFactory threadInitializer, final String name) {
        super(groupTimeSliceNS, threadInitializer);
        LOGGER = LoggerFactory.getLogger("TheChunkSystem/" + name);
        LOGGER.info("Initialized new LS ChunkSystem '{}' with {} allocated threads", name, workerThreadCount);
        this.adjustThreadCount(workerThreadCount);
    }

    private void wakeupIdleThread() {
        for (final WorkerThread thread : this.threads.getArray()) {
            if (thread.notifyTasks()) {
                return;
            }
        }
    }

    public Thread @NonNull [] getAliveThreads() {
        final WorkerThread[] threads = this.aliveThreads.getArray();

        return Arrays.copyOf(threads, threads.length, Thread[].class);
    }

    public Thread @NonNull [] getCoreThreads() {
        final WorkerThread[] threads = this.threads.getArray();

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
            for (final BalancedChunkSystem.OrderedStreamGroup.Queue queue : this.stream.queues.getArray()) {
                queue.shutdown();
            }
        }

        for (final WorkerThread thread : this.threads.getArray()) {
            thread.halt(false);
        }
    }

    /**
     * Waits until all threads in this pool have shutdown, or until the specified time has passed.
     *
     * @param msToWait
     *     Maximum time to wait.
     *
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
     *
     * @param msToWait
     *     Maximum time to wait.
     *
     * @return {@code false} if the maximum time passed, {@code true} otherwise.
     *
     * @throws InterruptedException
     *     If this thread is interrupted.
     */
    public boolean joinInterruptable(final long msToWait) throws InterruptedException {
        return this.join(msToWait, true);
    }

    private boolean join(final long msToWait, final boolean interruptible) throws InterruptedException {
        synchronized (this) {
            if (!this.shutdown) {
                throw new IllegalStateException("Attempting to join on non-shutdown pool");
            }
        }

        final long nsToWait = TimeUnit.MILLISECONDS.toNanos(msToWait);
        final long start = System.nanoTime();
        final long deadline = start + nsToWait;
        boolean interrupted = false;
        try {
            for (final BalancedChunkSystem.WorkerThread thread : this.aliveThreads.getArray()) {
                while (thread.thread.isAlive()) {
                    try {
                        if (msToWait > 0L) {
                            final long current = System.nanoTime();
                            if (current - deadline >= 0L) {
                                return false;
                            }
                            thread.thread.join(Duration.ofNanos(deadline - current));
                        }
                        else {
                            thread.thread.join();
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

    /**
     * Shuts down this thread pool, optionally waiting for all tasks to be executed. This function will invoke
     * {@link PrioritisedExecutor#shutdown()} on all created executors on this thread pool.
     *
     * @param wait
     *     Whether to wait for tasks to be executed
     */
    public void shutdown(final boolean wait) {
        synchronized (this) {
            this.shutdown = true;
        }

        for (final BalancedChunkSystem.OrderedStreamGroup.Queue queue : this.stream.queues.getArray()) {
            queue.shutdown();
        }

        for (final WorkerThread thread : this.threads.getArray()) {
            // none of these can be true or else NPE
            thread.close(false, false);
        }

        if (wait) {
            this.join(0L);
        }
    }

    private void die(final WorkerThread thread) {
        this.aliveThreads.remove(thread);
    }

    public void adjustThreadCount(final int threads) {
        synchronized (this) {
            if (this.shutdown) {
                return;
            }

            final BalancedChunkSystem.WorkerThread[] currentThreads = this.threads.getArray();
            if (threads == currentThreads.length) {
                // no adjustment needed
                return;
            }

            if (threads < currentThreads.length) {
                // we need to trim threads
                for (int i = 0, difference = currentThreads.length - threads; i < difference; ++i) {
                    final BalancedChunkSystem.WorkerThread remove = currentThreads[currentThreads.length - i - 1];

                    remove.halt(false);
                    this.threads.remove(remove);
                }
            }
            else {
                // we need to add threads
                for (int i = 0, difference = threads - currentThreads.length; i < difference; ++i) {
                    final LazyRunnable run = new LazyRunnable();
                    final BalancedChunkSystem.WorkerThread thread = new BalancedChunkSystem.WorkerThread(this.threadFactory.newThread(run));

                    run.setRunnable(thread);
                    this.aliveThreads.add(thread);
                    this.threads.add(thread);

                    thread.thread.start();
                }
            }
        }

        for (final BalancedChunkSystem.WorkerThread thread : this.threads.getArray()) {
            thread.notifyTasks();
        }
    }

    public BalancedChunkSystem.@NonNull OrderedStreamGroup createChunkOrderedStreamGroup() {
        return this.createChunkOrderedStreamGroup(new AtomicLong());
    }

    public BalancedChunkSystem.@NonNull OrderedStreamGroup createChunkOrderedStreamGroup(final AtomicLong subOrderGenerate) {
        synchronized (this) {
            if (this.shutdown) {
                throw new IllegalStateException("Queue is shutdown");
            }

            return this.stream;
        }
    }

    public final class OrderedStreamGroup {

        private final AtomicLong subOrderGenerator;
        private final COWArrayList<Queue> queues = new COWArrayList<>(Queue.class);

        public OrderedStreamGroup(final AtomicLong subOrderGenerator) {
            this.subOrderGenerator = subOrderGenerator;
        }

        public boolean hasAnyTasks() {
            for (final Queue queue : this.queues.getArray()) {
                // note: we could use hasNoScheduledTasks(); however hasNoScheduledTasks() does not imply that
                //       peekFirst != null which can cause spinning on the shared lock while trying to acquire a group
                if (queue.wrapped.peekFirst() != null) {
                    return true;
                }
            }

            return false;
        }

        public boolean executeTask() {
            for (; ; ) {
                final PrioritisedExecutor.PrioritisedTask task = this.peekTask();
                if (task == null) {
                    return false;
                }
                if (task.execute()) {
                    return true;
                }
            }
        }

        public PrioritisedExecutor.PrioritisedTask peekTask() {
            PrioritisedExecutor.PrioritisedTask highestTask = null;
            PrioritisedExecutor.PriorityState highestPriority = null;
            for (final Queue wrapper : this.queues.getArray()) {
                final PrioritisedTaskQueue queue = wrapper.wrapped;
                PrioritisedExecutor.PrioritisedTask first;
                PrioritisedExecutor.PriorityState state = null;

                // handle race condition where first entry is executed as we peek it
                // note: entry.getPriorityState() == null implies queue.peekFirst() != entry
                while ((first = queue.peekFirst()) != null && (state = first.getPriorityState()) == null) ;

                if (first != null) {
                    if (highestPriority == null || state.compareTo(highestPriority) < 0) {
                        highestTask = first;
                        highestPriority = state;
                    }
                }
                else if (queue.isShutdown() && queue.hasNoScheduledTasks()) {
                    // remove empty shutdown queues
                    this.queues.remove(wrapper);
                }
            }

            return highestTask;
        }

        public @NonNull Queue createExecutor() {
            synchronized (BalancedChunkSystem.this) {
                if (BalancedChunkSystem.this.shutdown) {
                    throw new IllegalStateException("Queue is shutdown");
                }

                final Queue ret = new Queue(this.subOrderGenerator);

                this.queues.add(ret);

                return ret;
            }
        }

        public final class Queue implements PrioritisedExecutor {

            private final PrioritisedTaskQueue wrapped;
            private final AtomicLong executors = new AtomicLong();
            private volatile boolean halt;

            public Queue(final AtomicLong subOrderGenerator) {
                this.wrapped = new PrioritisedTaskQueue(subOrderGenerator);
            }

            /**
             * Removes this queue from the thread pool without shutting the queue down or waiting for queued tasks to be
             * executed
             */
            public void halt() {
                this.halt = true;
                BalancedChunkSystem.OrderedStreamGroup.this.queues.remove(this);
            }

            /**
             * Returns whether this executor is scheduled to run tasks or is running tasks, otherwise it returns whether
             * this queue is not halted and not shutdown.
             */
            public boolean isActive() {
                if (this.halt) {
                    return this.executors.get() > 0L;
                }
                else {
                    if (!this.isShutdown()) {
                        return true;
                    }

                    return !this.wrapped.hasNoScheduledTasks();
                }
            }

            @Override
            public long getTotalTasksScheduled() {
                return this.wrapped.getTotalTasksScheduled();
            }

            @Override
            public long getTotalTasksExecuted() {
                return this.wrapped.getTotalTasksExecuted();
            }

            @Override
            public long generateNextSubOrder() {
                return this.wrapped.generateNextSubOrder();
            }

            @Override
            public boolean shutdown() {
                return this.wrapped.shutdown();
            }

            @Override
            public boolean isShutdown() {
                return this.wrapped.isShutdown();
            }

            @Contract("_ -> new")
            @Override
            public @NonNull PrioritisedTask createTask(final Runnable task) {
                return this.createTask(task, Priority.NORMAL);
            }

            @Contract("_, _ -> new")
            @Override
            public @NonNull PrioritisedTask createTask(final Runnable task, final Priority priority) {
                return this.createTask(task, priority, this.generateNextSubOrder(), 0L);
            }

            @Contract("_, _, _, _ -> new")
            @Override
            public @NonNull PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder, final long stream) {
                return new Task(this.wrapped.createTask(() -> {
                    Queue.this.executors.getAndIncrement();
                    try {
                        task.run();
                    } finally {
                        Queue.this.executors.getAndDecrement();
                    }
                }, priority, subOrder, stream));
            }

            @Override
            public @NonNull PrioritisedTask queueTask(final Runnable task) {
                final PrioritisedTask ret = this.createTask(task);
                ret.queue();
                return ret;
            }

            @Override
            public @NonNull PrioritisedTask queueTask(final Runnable task, final Priority priority) {
                final PrioritisedTask ret = this.createTask(task, priority);
                ret.queue();
                return ret;
            }

            @Override
            public @NonNull PrioritisedTask queueTask(final Runnable task, final Priority priority, final long subOrder, final long stream) {
                final PrioritisedTask ret = this.createTask(task, priority, subOrder, stream);
                ret.queue();
                return ret;
            }

            @Override
            public boolean executeTask() {
                return this.wrapped.executeTask();
            }

            private final class Task implements PrioritisedTask {

                private final PrioritisedTask wrap;

                public Task(final PrioritisedTask wrap) {
                    this.wrap = wrap;
                }

                @Override
                public PrioritisedExecutor getExecutor() {
                    return Queue.this;
                }

                @Override
                public boolean queue() {
                    if (this.wrap.queue()) {
                        BalancedChunkSystem.this.wakeupIdleThread();
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean isQueued() {
                    return this.wrap.isQueued();
                }

                @Override
                public boolean cancel() {
                    return this.wrap.cancel();
                }

                @Override
                public boolean execute() {
                    return this.wrap.execute();
                }

                @Override
                public Priority getPriority() {
                    return this.wrap.getPriority();
                }

                @Override
                public boolean setPriority(final Priority priority) {
                    return this.wrap.setPriority(priority);
                }

                @Override
                public boolean raisePriority(final Priority priority) {
                    return this.wrap.raisePriority(priority);
                }

                @Override
                public boolean lowerPriority(final Priority priority) {
                    return this.wrap.lowerPriority(priority);
                }

                @Override
                public long getSubOrder() {
                    return this.wrap.getSubOrder();
                }

                @Override
                public boolean setSubOrder(final long subOrder) {
                    return this.wrap.setSubOrder(subOrder);
                }

                @Override
                public boolean raiseSubOrder(final long subOrder) {
                    return this.wrap.raiseSubOrder(subOrder);
                }

                @Override
                public boolean lowerSubOrder(final long subOrder) {
                    return this.wrap.lowerSubOrder(subOrder);
                }

                @Override
                public long getStream() {
                    return this.wrap.getStream();
                }

                @Override
                public boolean setStream(final long stream) {
                    return this.wrap.setStream(stream);
                }

                @Override
                public boolean setPrioritySubOrderStream(final Priority priority, final long subOrder, final long stream) {
                    return this.wrap.setPrioritySubOrderStream(priority, subOrder, stream);
                }

                @Override
                public PriorityState getPriorityState() {
                    return this.wrap.getPriorityState();
                }
            }
        }
    }

    private final class WorkerThread extends QueueExecutorRunnable {

        public WorkerThread(final Thread thread) {
            super(thread, null);
        }

        @Override
        protected void die() {
            BalancedChunkSystem.this.die(this);
        }

        @Override
        protected boolean pollTasks() {
            boolean ret = false;
            final long deadline = System.nanoTime() + BalancedChunkSystem.this.groupTimeSliceNS;
            do {
                if (this.halted) break;
                try {
                    if (!BalancedChunkSystem.this.stream.executeTask()) break;
                    ret = true;
                } catch (final Throwable thrown) {
                    LOGGER.error("Exception thrown from thread '" + this.thread.getName(), thrown);
                }
            } while (System.nanoTime() - deadline <= 0L);
            return ret;
        }
    }
}
