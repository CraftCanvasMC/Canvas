package io.canvasmc.amethyst.sampler.async;

import io.canvasmc.amethyst.ProfilerException;
import io.canvasmc.amethyst.sampler.SamplerSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.jspecify.annotations.Nullable;

public final class AsyncProfilerControl {
    private static final List<String> CPU_ENGINES = List.of("cpu", "ctimer");

    @Nullable
    private static AsyncProfiler PROFILER;

    private AsyncProfilerControl() {
    }

    /**
     * Gets the reason for if this is unsupported
     *
     * @return {@code null} if it is supported, otherwise it's not supported :/
     */
    @Nullable
    public static String unsupportedReason() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return "async-profiler only supports Linux and macOS";
        }
        if (!AsyncProfilerLoader.isSupported()) {
            return "async-profiler doesn't support this platform (" + System.getProperty("os.arch") + ")";
        }
        return null;
    }

    public static String version() {
        try {
            return profiler().getVersion();
        } catch (final ProfilerException ignored) {
            return "unknown";
        }
    }

    @Nullable
    public static synchronized String start(
        final SamplerSettings settings,
        final Path jfr,
        final Consumer<String> warn
    ) {
        if (jfr.toAbsolutePath().toString().contains(",")) {
            throw new ProfilerException("The profiler output path can't contain commas: " + jfr.toAbsolutePath());
        }

        for (final String engine : CPU_ENGINES) {
            try {
                execute(command(settings, engine, jfr));
                return engine;
            } catch (final ProfilerException failed) {
                warn.accept("The " + engine + " engine couldn't start: " + failed.getMessage());
            }
        }

        // wall needs no permissions, so it still runs when no other cpu engine can
        execute(command(settings, null, jfr));
        warn.accept("No cpu engine could start, only wall time is sampled");

        return null;
    }

    public static synchronized void stop() {
        execute("stop");
    }

    public static synchronized void addThread(final Thread thread) {
        profiler().addThread(thread);
    }

    private static void execute(final String command) {
        try {
            profiler().execute(command);
        } catch (final IOException | IllegalStateException | IllegalArgumentException thrown) {
            throw new ProfilerException("async-profiler refused '" + command + "': " + thrown.getMessage(), thrown);
        }
    }

    private static String command(
        final SamplerSettings settings,
        @Nullable
        final String engine,
        final Path jfr
    ) {
        final long wall = settings.wallInterval().toNanos();
        final StringBuilder command = new StringBuilder("start");
        if (engine == null) {
            command.append(",event=wall,interval=").append(wall);
        }
        else {
            command.append(",event=").append(engine).append(",interval=").append(settings.cpuInterval().toNanos())
                .append(",wall=").append(wall);
        }

        // only limits wall, cpu still samples every thread
        command.append(",filter");
        if (settings.allocation()) {
            command.append(",alloc=").append(settings.allocationInterval());
        }
        if (settings.lock()) {
            command.append(",lock=").append(settings.lockThreshold().toNanos());
        }

        // HAS to be monotonic, so sample times line up with System.nanoTime()
        return command.append(",clock=monotonic,jfr,file=").append(jfr.toAbsolutePath()).toString();
    }

    private static synchronized AsyncProfiler profiler() {
        if (PROFILER == null) {
            final String unsupported = unsupportedReason();
            if (unsupported != null) {
                throw new ProfilerException(unsupported);
            }

            try {
                PROFILER = AsyncProfilerLoader.load();
            } catch (final IOException | RuntimeException | UnsatisfiedLinkError thrown) {
                throw new ProfilerException("Couldn't load async-profiler: " + thrown.getMessage(), thrown);
            }
        }
        return PROFILER;
    }
}
