package io.canvasmc.canvas.util;

import com.mojang.logging.LogUtils;
import io.canvasmc.canvas.spark.MultiLoopThreadDumper;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class NamedAgnosticThreadFactory<T extends Thread> implements ThreadFactory {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final ThreadBuilderFunction<T> typeOfThread;
    private final int priority;

    public NamedAgnosticThreadFactory(String name, ThreadBuilderFunction<T> typeOfThread, int priority) {
        this.typeOfThread = typeOfThread;
        this.priority = priority;
        SecurityManager securityManager = System.getSecurityManager();
        this.group = securityManager != null ? securityManager.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = name + "-";
        MultiLoopThreadDumper.REGISTRY.add(namePrefix);
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        T thread = typeOfThread.apply(this.group, runnable, this.namePrefix + this.threadNumber.getAndIncrement());
        thread.setUncaughtExceptionHandler((threadx, throwable) -> {
            LOGGER.error("Caught exception in thread {} from {}", threadx, runnable);
            LOGGER.error("", throwable);
        });
        if (thread.getPriority() != priority) {
            thread.setPriority(priority);
        }

        return thread;
    }

    public interface ThreadBuilderFunction<T extends Thread> {
        T apply(ThreadGroup threadGroup, Runnable runnable, String name);
    }
}
