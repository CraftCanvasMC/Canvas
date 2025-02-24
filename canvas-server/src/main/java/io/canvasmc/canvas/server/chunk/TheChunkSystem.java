package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.Priority;
import com.ishland.flowsched.executor.ExecutorManager;
import com.ishland.flowsched.executor.WorkerThread;
import io.canvasmc.canvas.util.ThreadBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TheChunkSystem extends ExecutorManager {
    private final Logger LOGGER;
    private final String name;
    private boolean shutdown;

    private final TheChunkSystem.COWArrayList<TheChunkSystem.ExecutorGroup> executors = new TheChunkSystem.COWArrayList<>(TheChunkSystem.ExecutorGroup.class);

    public TheChunkSystem(final int workerThreadCount, final ThreadBuilder threadInitializer, final String name) {
        super(workerThreadCount, threadInitializer, 16);
        LOGGER = LoggerFactory.getLogger("TheChunkSystem/"  + name);
        this.name = name;
        LOGGER.info("Initialized new ChunkSystem with {} allocated threads", workerThreadCount);
    }

    public int getAliveThreads() {
        int count = 0;
        for (final WorkerThread thread : this.workerThreads) {
            if (thread.active) {
                count++;
            }
        }
        return count;
    }

    public Thread[] getCoreThreads() {
        final WorkerThread[] threads = this.workerThreads;

        return Arrays.copyOf(threads, threads.length, Thread[].class);
    }

    @Override
    public void shutdown() {
        synchronized (this) {
            this.shutdown = true;
        }

        // shutdown workers
        super.shutdown();
        this.wakeup();

        for (final TheChunkSystem.ExecutorGroup group : this.executors.getArray()) {
            for (final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor executor : group.executors.getArray()) {
                executor.shutdown();
            }
        }

        LOGGER.info("Shutdown ChunkSystem '{}' successfully", this.name);
    }

    private void notifyAllThreads() {
        this.wakeup();
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

        public TheChunkSystem.ExecutorGroup.@NotNull ThreadPoolExecutor createExecutor() {
            synchronized (TheChunkSystem.this) {
                if (TheChunkSystem.this.shutdown) {
                    throw new IllegalStateException("Queue is shutdown: " + TheChunkSystem.this);
                }

                final TheChunkSystem.ExecutorGroup.ThreadPoolExecutor ret = new TheChunkSystem.ExecutorGroup.ThreadPoolExecutor();

                this.executors.add(ret);

                return ret;
            }
        }

        public final class ThreadPoolExecutor implements PrioritisedExecutor {
            // only use this method for building tasks. nothing else
            private final ChunkSystemTaskQueue taskBuilder = new ChunkSystemTaskQueue(TheChunkSystem.this);
            private volatile boolean halt;

            private ThreadPoolExecutor() {
            }

            private TheChunkSystem.ExecutorGroup getGroup() {
                return TheChunkSystem.ExecutorGroup.this;
            }

            private void notifyPriorityShift() {
                TheChunkSystem.this.notifyAllThreads();
            }

            private void notifyScheduled() {
                TheChunkSystem.this.notifyAllThreads();
            }

            /**
             * Removes this queue from the thread pool without shutting the queue down or waiting for queued tasks to be executed
             */
            public void halt() {
                this.halt = true;

                TheChunkSystem.ExecutorGroup.this.executors.remove(this);
            }

            public boolean isActive() {
                if (this.halt) {
                    return false;
                } else {
                    if (!this.isShutdown()) {
                        return true;
                    }

                    return !TheChunkSystem.this.globalWorkQueue.isEmpty();
                }
            }

            @Override
            public boolean shutdown() {
                if (TheChunkSystem.this.globalWorkQueue.isEmpty()) {
                    TheChunkSystem.ExecutorGroup.this.executors.remove(this);
                }

                return true;
            }

            @Override
            public boolean isShutdown() {
                return TheChunkSystem.this.shutdown;
            }

            @Override
            public long getTotalTasksScheduled() {
                return 0; // Canvas // TODO: implement
            }

            @Override
            public long getTotalTasksExecuted() {
                return 0; // Canvas // TODO: implement
            }

            @Override
            public long generateNextSubOrder() {
                return TheChunkSystem.ExecutorGroup.this.subOrderGenerator.getAndIncrement();
            }

            @Override
            public boolean executeTask() {
                throw new UnsupportedOperationException("Unable to execute task from ThreadPoolExecutor as interface into FlowSched");
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
                return new TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.WrappedTask(this.taskBuilder.createTask(task, priority, subOrder));
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
                            // technically we don't have a "high priority alert"
                            TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyScheduled();
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
                        // technically we don't have a "high priority alert"
                        TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyPriorityShift();
                        return true;
                    }

                    return false;
                }

                @Override
                public boolean raisePriority(final Priority priority) {
                    if (this.wrapped.raisePriority(priority)) {
                        // technically we don't have a "high priority alert"
                        TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyPriorityShift();
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
                        // technically we don't have a "high priority alert"
                        TheChunkSystem.ExecutorGroup.ThreadPoolExecutor.this.notifyPriorityShift();
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
            this.array = (E[]) Array.newInstance(clazz, 0);
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
