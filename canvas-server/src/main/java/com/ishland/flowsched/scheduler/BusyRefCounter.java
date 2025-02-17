package com.ishland.flowsched.scheduler;

import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.util.Objects;

public class BusyRefCounter {

    private final ReferenceList<Runnable> onComplete = new ReferenceArrayList<>();
    private Runnable onCompleteOnce = null;
    private int counter = 0;

    public synchronized boolean isBusy() {
        return counter != 0;
    }

    public void addListener(Runnable runnable) {
        Objects.requireNonNull(runnable);
        boolean runNow = false;
        synchronized (this) {
            if (!isBusy()) {
                runNow = true;
            } else {
                onComplete.add(runnable);
            }
        }
        if (runNow) {
            runnable.run();
        }
    }

    public void addListenerOnce(Runnable runnable) {
        Objects.requireNonNull(runnable);
        boolean runNow = false;
        synchronized (this) {
            if (!isBusy()) {
                runNow = true;
            } else {
                onCompleteOnce = runnable;
            }
        }
        if (runNow) {
            runnable.run();
        }
    }

    public synchronized void incrementRefCount() {
        counter ++;
    }

    public void decrementRefCount() {
        Runnable[] onCompleteArray = null;
        Runnable onCompleteOnce = null;
        synchronized (this) {
            Assertions.assertTrue(counter > 0);
            if (--counter == 0) {
                onCompleteArray = onComplete.toArray(Runnable[]::new);
                onComplete.clear();
            }
            onCompleteOnce = this.onCompleteOnce;
            this.onCompleteOnce = null;
        }
        if (onCompleteArray != null) {
            for (Runnable runnable : onCompleteArray) {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        if (onCompleteOnce != null) {
            try {
                onCompleteOnce.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

}
