package com.ishland.flowsched.executor;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.ishland.flowsched.structs.DynamicPriorityQueue;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class ExecutorManager {

    public final DynamicPriorityQueue<Task> globalWorkQueue; // Canvas - private -> protected
    protected final ConcurrentMap<LockToken, FreeableTaskList> lockListeners = new ConcurrentHashMap<>(); // Canvas - private -> protected
    final Object workerMonitor = new Object();
    protected final WorkerThread[] workerThreads; // Canvas - private -> protected

    /**
     * Creates a new executor manager.
     *
     * @param workerThreadCount the number of worker threads.
     */
    public ExecutorManager(int workerThreadCount) {
        this(workerThreadCount, thread -> {});
    }

    /**
     * Creates a new executor manager.
     *
     * @param workerThreadCount the number of worker threads.
     * @param threadInitializer the thread initializer.
     */
    public ExecutorManager(int workerThreadCount, Consumer<Thread> threadInitializer) {
        globalWorkQueue = new DynamicPriorityQueue<>();
        workerThreads = new WorkerThread[workerThreadCount];
        for (int i = 0; i < workerThreadCount; i++) {
            final WorkerThread thread = new WorkerThread(this);
            threadInitializer.accept(thread);
            thread.start();
            workerThreads[i] = thread;
        }
    }

    /**
     * Attempt to lock the given tokens.
     * The caller should discard the task if this method returns false, as it reschedules the task.
     *
     * @return {@code true} if the lock is acquired, {@code false} otherwise.
     */
    boolean tryLock(Task task) {
        retry:
        while (true) {
            final FreeableTaskList listenerSet = new FreeableTaskList();
            LockToken[] lockTokens = task.lockTokens();
            for (int i = 0; i < lockTokens.length; i++) {
                LockToken token = lockTokens[i];
                final FreeableTaskList present = this.lockListeners.putIfAbsent(token, listenerSet);
                if (present != null) {
                    for (int j = 0; j < i; j++) {
                        this.lockListeners.remove(lockTokens[j], listenerSet);
                    }
                    callListeners(listenerSet); // synchronizes
                    synchronized (present) {
                        if (present.freed) {
                            continue retry;
                        } else {
                            present.add(task);
                        }
                    }
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Release the locks held by the given task.
     * @param task the task.
     */
    void releaseLocks(Task task) {
        FreeableTaskList expectedListeners = null;
        for (LockToken token : task.lockTokens()) {
            final FreeableTaskList listeners = this.lockListeners.remove(token);
            if (listeners != null) {
                if (expectedListeners == null) {
                    expectedListeners = listeners;
                } else {
                    Assertions.assertTrue(expectedListeners == listeners, "Inconsistent lock listeners");
                }
            } else {
                throw new IllegalStateException("Lock token " + token + " is not locked");
            }
        }
        if (expectedListeners != null) {
            callListeners(expectedListeners); // synchronizes
        }
    }

    private void callListeners(FreeableTaskList listeners) {
        synchronized (listeners) {
            listeners.freed = true;
            if (listeners.isEmpty()) return;
            for (Task listener : listeners) {
                this.schedule0(listener);
            }
        }
        this.wakeup();
    }

    /**
     * Polls an executable task from the global work queue.
     * @return the task, or {@code null} if no task is executable.
     */
    Task pollExecutableTask() {
        Task task;
        while ((task = this.globalWorkQueue.dequeue()) != null) {
            if (this.tryLock(task)) {
                return task;
            }
        }
        return null;
    }

    /**
     * Shuts down the executor manager.
     */
    public void shutdown() {
        for (WorkerThread workerThread : workerThreads) {
            workerThread.shutdown();
        }
    }

    /**
     * Schedules a task.
     * @param task the task.
     */
    public void schedule(Task task) {
        schedule0(task);
        wakeup();
    }

    private void schedule0(Task task) {
        this.globalWorkQueue.enqueue(task, task.priority());
    }

    public void wakeup() { // Canvas - private -> public
        synchronized (this.workerMonitor) {
            this.workerMonitor.notify();
        }
    }

    public boolean hasPendingTasks() {
        return this.globalWorkQueue.size() != 0;
    }

    /**
     * Schedules a runnable for execution with the given priority.
     *
     * @param runnable the runnable.
     * @param priority the priority.
     */
    public void schedule(Runnable runnable, Priority priority) {
        this.schedule(new SimpleTask(runnable, priority));
    }

    /**
     * Creates an executor that schedules runnables with the given priority.
     *
     * @param priority the priority.
     * @return the executor.
     */
    public Executor executor(Priority priority) {
        return runnable -> this.schedule(runnable, priority);
    }

    /**
     * Notifies the executor manager that the priority of the given task has changed.
     *
     * @param task the task.
     */
    public void notifyPriorityChange(Task task) {
        this.globalWorkQueue.changePriority(task, task.priority());
    }

    protected static class FreeableTaskList extends ReferenceArrayList<Task> { // Canvas - private -> protected

        private boolean freed = false;

    }

}
