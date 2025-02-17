package io.canvasmc.canvas.util;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AsyncProcessor {
    private static final Logger LOGGER = LogManager.getLogger(AsyncProcessor.class);
    private final BlockingQueue<Runnable> taskQueue;
    private final Thread workerThread;
    private volatile boolean isRunning;

    public AsyncProcessor(String threadName) {
        this.taskQueue = new LinkedBlockingQueue<>();
        this.isRunning = true;

        this.workerThread = new TickThread(() -> {
            while (isRunning || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("An unexpected error occurred when running async processor: {}", e.getMessage(), e);
                }
            }
        }, threadName);

        this.workerThread.start();
    }

    /**
     * Submits a task to be executed asynchronously.
     *
     * @param task The task to execute
     */
    public void submit(Runnable task) {
        if (!isRunning) {
            throw new IllegalStateException("AsyncExecutor is not running.");
        }
        taskQueue.offer(task);
    }

    /**
     * Gracefully shuts down the executor after processing all tasks in the queue.
     */
    public void shutdown() {
        isRunning = false;
        workerThread.interrupt();
    }

    /**
     * Forcefully stops the executor, clearing all pending tasks in the queue.
     */
    public void shutdownNow() {
        isRunning = false;
        workerThread.interrupt();
        taskQueue.clear();
    }

    /**
     * Checks if the executor is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }
}
