package io.canvasmc.amethyst.sampler;

import io.canvasmc.amethyst.ProfileClock;
import io.canvasmc.amethyst.ProfilerException;
import io.canvasmc.amethyst.attribution.ThreadIntervals;
import io.canvasmc.amethyst.proto.Samples;
import io.canvasmc.amethyst.sampler.async.AsyncProfilerControl;
import io.canvasmc.amethyst.sampler.jfr.JfrSampleReader;
import io.canvasmc.amethyst.source.SourceResolver;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class Sampler {
    private final SamplerSettings settings;
    private final JfrSampleReader reader;

    private boolean running;

    @Nullable
    private String engine;

    public Sampler(final SamplerSettings settings, final ProfileClock clock, final SourceResolver sources) {
        this.settings = settings;
        this.reader = new JfrSampleReader(
            clock,
            settings.cpuInterval().toNanos(),
            settings.wallInterval().toNanos(),
            sources
        );
    }

    @Nullable
    public String start(final Path jfr, final Collection<Thread> gameThreads, final Consumer<String> warn) {
        if (running) {
            throw new ProfilerException("The sampler is already running");
        }
        engine = AsyncProfilerControl.start(settings, jfr, warn);
        running = true;
        for (final Thread thread : gameThreads) {
            addGameThread(thread);
        }
        return engine;
    }

    public void addGameThread(final Thread thread) {
        if (!running) {
            throw new ProfilerException("The sampler isn't running");
        }
        AsyncProfilerControl.addThread(thread);
        reader.addGameThread(thread.threadId());
    }

    public void stop() {
        if (!running) {
            throw new ProfilerException("The sampler isn't running");
        }
        AsyncProfilerControl.stop();
        running = false;
    }

    public boolean running() {
        return running;
    }

    @Nullable
    public String engine() {
        // this is null if CPU wasnt sampled
        return engine;
    }

    public void read(
        final Path jfrFile,
        final Collection<ThreadIntervals.View> intervals,
        final LongPredicate keepHandle
    ) {
        try {
            reader.read(jfrFile, intervals, keepHandle);
        } catch (final IOException thrown) {
            throw new ProfilerException("Couldn't read the samples in " + jfrFile + ": " + thrown.getMessage(), thrown);
        }
    }

    public Samples write() {
        return reader.write(engine != null, settings.allocation(), settings.lock());
    }
}
