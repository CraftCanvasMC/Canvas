package io.canvasmc.canvas.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class QueuedTaskScheduler {
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    public void schedule(Runnable task) {
        taskQueue.add(task);
    }

    public void tick() {
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable throwable) {
                if (throwable instanceof ThreadDeath) throw throwable;
                else throwable.printStackTrace();
            }
        }
    }
}
