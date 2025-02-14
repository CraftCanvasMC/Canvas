package com.ishland.flowsched.scheduler;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DaemonizedStatusAdvancingScheduler<K, V, Ctx, UserData> extends StatusAdvancingScheduler<K, V, Ctx, UserData> {

    protected final Thread thread;
    private final Object notifyMonitor = new Object();
    private final AtomicInteger taskSize = new AtomicInteger();
    private final Queue<Runnable> taskQueue;
    private final Executor executor;
    private final Scheduler scheduler;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    protected DaemonizedStatusAdvancingScheduler(ThreadFactory threadFactory) {
        this(threadFactory, new ObjectFactory.DefaultObjectFactory());
    }

    protected DaemonizedStatusAdvancingScheduler(ThreadFactory threadFactory, ObjectFactory objectFactory) {
        super(objectFactory);
        this.thread = threadFactory.newThread(this::mainLoop);
        this.taskQueue = objectFactory.newMPSCQueue();
        this.executor = e -> {
            final boolean needWakeup = this.taskSize.getAndIncrement() == 0;
            taskQueue.add(e);
            if (needWakeup) wakeUp();
        };
        this.scheduler = Schedulers.from(this.executor);
    }

    private void mainLoop() {
        main_loop:
        while (true) {
            if (pollTasks()) {
                continue;
            }
            if (this.shutdown.get()) {
                return;
            }

//            // attempt to spin-wait before sleeping
//            if (!pollTasks()) {
//                Thread.interrupted(); // clear interrupt flag
//                for (int i = 0; i < 5000; i ++) {
//                    if (pollTasks()) continue main_loop;
//                    LockSupport.parkNanos("Spin-waiting for tasks", 10_000); // 100us
//                }
//            }

//            LockSupport.parkNanos("Waiting for tasks", 1_000_000); // 1ms
            synchronized (this.notifyMonitor) {
                if (this.continueProcessWork() || this.shutdown.get()) continue main_loop;
                try {
                    this.notifyMonitor.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private boolean pollTasks() {
        boolean hasWork = false;
        Runnable runnable;
        while ((runnable = taskQueue.poll()) != null) {
            this.taskSize.decrementAndGet();
            hasWork = true;
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace(); // TODO exception handling
            }
        }
        hasWork |= this.tick();
        return hasWork;
    }

    public void waitTickSync() {
        if (Thread.currentThread() == this.thread) {
            throw new IllegalStateException("Cannot wait sync on scheduler thread");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.getExecutor().execute(() -> future.complete(null));
        future.join();
    }

    @Override
    protected final Executor getExecutor() {
        return this.executor;
    }

    @Override
    protected final Scheduler getSchedulerBackedByExecutor() {
        return this.scheduler;
    }

    @Override
    protected Executor getBackgroundExecutor() {
        return this.getExecutor();
    }

    @Override
    protected Scheduler getSchedulerBackedByBackgroundExecutor() {
        return this.getSchedulerBackedByExecutor();
    }

    @Override
    protected boolean hasPendingUpdates() {
        return !this.taskQueue.isEmpty() || super.hasPendingUpdates();
    }

    @Override
    protected boolean continueProcessWork() {
        return this.taskSize.get() != 0 || super.continueProcessWork();
    }

    @Override
    protected void markDirty(K key) {
        super.markDirty(key);
    }

    protected void wakeUp() {
        synchronized (this.notifyMonitor) {
            this.notifyMonitor.notify();
        }
    }

    public void shutdown() {
        shutdown.set(true);
        wakeUp();
    }

}
