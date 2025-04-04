package com.ishland.flowsched.executor;

import ca.spottedleaf.concurrentutil.util.Priority;
import java.util.Objects;

public class SimpleTask implements Task {

    private final Runnable wrapped;
    private final int priority;

    public SimpleTask(Runnable wrapped, int priority) {
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
    public int priority() {
        return this.priority;
    }
}
