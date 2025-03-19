package com.ishland.flowsched.executor;

import ca.spottedleaf.concurrentutil.util.Priority;
import java.util.Objects;

public class SimpleTask implements Task {

    private final Runnable wrapped;
    private final Priority priority;

    public SimpleTask(Runnable wrapped, Priority priority) {
        this.wrapped = Objects.requireNonNull(wrapped);
        this.priority = priority;
    }

    @Override
    public void run(Runnable releaseLocks) {
        try {
            wrapped.run();
        } finally {
            releaseLocks.run();
        }
    }

    @Override
    public void propagateException(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public LockToken[] lockTokens() {
        return new LockToken[0];
    }

    @Override
    public Priority priority() {
        return this.priority;
    }
}
