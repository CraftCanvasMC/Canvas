package com.ishland.flowsched.scheduler;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CancellationSignaller {

    public static final CancellationSignaller COMPLETED;

    static {
        final CancellationSignaller signaller = new CancellationSignaller(unused -> {
        });
        signaller.finished.set(Optional.empty());
        signaller.cancelled.set(true);
        COMPLETED = signaller;
    }

    private final ReferenceList<Consumer<Throwable>> onComplete = new ReferenceArrayList<>();
    private final Consumer<CancellationSignaller> cancel;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Optional<Throwable>> finished = new AtomicReference<>(null);

    public CancellationSignaller(Consumer<CancellationSignaller> cancel) {
        this.cancel = Objects.requireNonNull(cancel);
    }

    public void fireComplete(Throwable throwable) {
        if (finished.compareAndSet(null, Optional.ofNullable(throwable))) {
            final Consumer<Throwable>[] consumers;
            synchronized (this) {
                consumers = onComplete.toArray(Consumer[]::new);
                onComplete.clear();
            }
            for (Consumer<Throwable> consumer : consumers) {
                try {
                    consumer.accept(throwable);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void addListener(Consumer<Throwable> callback) {
        if (finished.get() != null) {
            callback.accept(finished.get().orElse(null));
            return;
        }
        synchronized (this) {
            if (finished.get() != null) {
                callback.accept(finished.get().orElse(null));
                return;
            }
            onComplete.add(callback);
        }
    }

    public void cancel() {
        if (this.cancelled.compareAndSet(false, true)) {
            this.cancel.accept(this);
        }
    }

}
