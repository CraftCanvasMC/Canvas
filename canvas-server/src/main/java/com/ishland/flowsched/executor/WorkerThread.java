package com.ishland.flowsched.executor;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerThread extends TickThread { // Canvas - extend TickThread

    private static final Logger LOGGER = LoggerFactory.getLogger("FlowSched Executor Worker Thread");

    private final ExecutorManager executorManager;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    public volatile boolean active = false; // Canvas - add activity boolean for api

    public WorkerThread(ExecutorManager executorManager) {
        super("null_worker"); // Canvas - extend TickThread
        this.executorManager = executorManager;
    }

    @Override
    public void run() {
        main_loop:
        while (true) {
            if (this.shutdown.get()) {
                return;
            }
            active = true; // Canvas - add activity boolean for api
            if (pollTasks()) {
                continue;
            }

//            // attempt to spin-wait before sleeping
//            if (!pollTasks()) {
//                Thread.interrupted(); // clear interrupt flag
//                for (int i = 0; i < 1000; i ++) {
//                    if (pollTasks()) continue main_loop;
//                    LockSupport.parkNanos("Spin-waiting for tasks", 10_000); // 10us
//                }
//            }

//            LockSupport.parkNanos("Waiting for tasks", 1_000_000); // 1ms
            synchronized (this.executorManager.workerMonitor) {
                if (this.executorManager.hasPendingTasks()) continue main_loop;
                try {
                    active = false; // Canvas - add activity boolean for api
                    this.executorManager.workerMonitor.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private boolean pollTasks() {
        final Task task = executorManager.pollExecutableTask();
        try {
            if (task != null) {
                AtomicBoolean released = new AtomicBoolean(false);
                try {
                    task.run(() -> {
                        if (released.compareAndSet(false, true)) {
                            executorManager.releaseLocks(task);
                        }
                    });
                } catch (Throwable t) {
                    try {
                        if (released.compareAndSet(false, true)) {
                            executorManager.releaseLocks(task);
                        }
                    } catch (Throwable t1) {
                        t.addSuppressed(t1);
                        LOGGER.error("Exception thrown while releasing locks", t);
                    }
                    try {
                        task.propagateException(t);
                    } catch (Throwable t1) {
                        t.addSuppressed(t1);
                        LOGGER.error("Exception thrown while propagating exception", t);
                    }
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            LOGGER.error("Exception thrown while executing task", t);
            return true;
        }
    }

    public void shutdown() {
        shutdown.set(true);
        LockSupport.unpark(this);
    }


}
