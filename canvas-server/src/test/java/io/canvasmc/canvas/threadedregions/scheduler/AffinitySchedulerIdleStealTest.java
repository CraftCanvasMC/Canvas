package io.canvasmc.canvas.threadedregions.scheduler;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Normal
class AffinitySchedulerIdleStealTest {

    private static final long PERIOD = TimeUnit.MILLISECONDS.toNanos(50L);

    private static final class TestTick extends SchedulableTick {

        private final AtomicInteger ticks = new AtomicInteger();
        private volatile long busyNanos;
        private volatile boolean cancelled;

        private TestTick(final long start, final long busyNanos) {
            this.setScheduledStart(start);
            this.busyNanos = busyNanos;
        }

        @Override
        public boolean runTick() {
            if (this.cancelled) {
                return false;
            }
            this.ticks.incrementAndGet();
            final long end = System.nanoTime() + this.busyNanos;
            while (System.nanoTime() - end < 0L) {
                Thread.onSpinWait();
            }
            final long next = this.getScheduledStart() + PERIOD;
            final long now = System.nanoTime();
            this.setScheduledStart(next - now < 0L ? now : next);
            return true;
        }

        @Override
        public boolean hasTasks() {
            return false;
        }

        @Override
        public boolean runTasks(final BooleanSupplier canContinue) {
            return true;
        }
    }

    @Test
    void idleRunnerTakesOverTicksOfABusyRunner() throws InterruptedException {
        final List<Throwable> failures = new CopyOnWriteArrayList<>();
        final AffinitySchedulerThreadPool scheduler = new AffinitySchedulerThreadPool(
            2,
            (runnable) -> {
                final Thread thread = new Thread(runnable, "Affinity scheduler test runner");
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((ignored, thrown) -> failures.add(thrown));
                return thread;
            },
            TimeUnit.MICROSECONDS.toNanos(80L),
            TimeUnit.MILLISECONDS.toNanos(AffinitySchedulerThreadPool.DEFAULT_STEAL_THRESH_MILLIS),
            () -> false,
            false
        );
        scheduler.start();
        try {
            final TestTick heavy = new TestTick(System.nanoTime(), 0L);
            final TestTick blocker = new TestTick(System.nanoTime(), TimeUnit.MILLISECONDS.toNanos(200L));
            scheduler.schedule(heavy);
            scheduler.schedule(blocker);
            Thread.sleep(10L);
            final TestTick light = new TestTick(System.nanoTime(), 0L);
            scheduler.schedule(light);
            blocker.cancelled = true;

            Thread.sleep(500L);
            heavy.busyNanos = TimeUnit.MILLISECONDS.toNanos(150L);
            Thread.sleep(300L);

            final int before = light.ticks.get();
            Thread.sleep(2_000L);
            final int ticked = light.ticks.get() - before;
            assertTrue(ticked >= 30, "light tick ran " + ticked + " times in 2 seconds while the other runner had nothing to do");
        } finally {
            scheduler.halt();
            scheduler.join(5_000L);
        }
        assertTrue(failures.isEmpty(), failures::toString);
    }
}
