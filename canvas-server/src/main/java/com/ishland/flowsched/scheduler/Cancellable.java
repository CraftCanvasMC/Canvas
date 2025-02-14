package com.ishland.flowsched.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

public class Cancellable {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        this.cancelled.set(true);
    }

    public boolean isCancelled() {
        return this.cancelled.get();
    }

}
