package io.canvasmc.canvas.subcommands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.commands.SubCommand;
import io.canvasmc.canvas.threadedregions.profiler.Session;
import io.canvasmc.canvas.threadedregions.profiler.platform.ProfilerPlatform;
import io.canvasmc.canvas.threadedregions.profiler.platform.SparkRegionProfiler;
import io.canvasmc.canvas.util.LockedReference;
import io.canvasmc.canvas.util.ReadWriteLockedReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.Nullable;

public class RegionDataCommand implements SubCommand {
    public static final Pattern SPARK_PROFILER_START_REGEX = Pattern.compile("^spark\\s+profiler.*");

    // this is the default platform we will use
    private static final ProfilerPlatform SPARK_PLATFORM = new SparkRegionProfiler();
    private static final ReadWriteLockedReference<Session> SESSION = new ReadWriteLockedReference<>(null);

    private final LockedReference<ProfilerPlatform> currentPlatform = new LockedReference<>(SPARK_PLATFORM);

    public static boolean isProfiling() {
        return SESSION.isSet();
    }

    public static void computeIfProfiling(final Consumer<Session> ifPresent, final @Nullable Runnable orElse) {
        SESSION.runIfPresentOrElse(ifPresent, orElse);
    }

    @Override
    public String getDescription() {
        return "Allows accessing and profiling region data";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base; // TODO - entity list(with filters), player list, region profile, tile entities, mob caps, scheduling info, tick scheduling
    }

    @Override
    public String getName() {
        return "regiondata";
    }

    /**
     * Replaces the current platform implementation with a new platform
     *
     * @param newPlatform
     *     the new profiler to use
     *
     * @throws java.lang.IllegalStateException
     *     if currently profiling
     */
    public void replacePlatform(final ProfilerPlatform newPlatform) {
        computeIfProfiling((_) -> {
            throw new IllegalStateException("Unable to modify platform during profiling session");
        }, () -> {
            currentPlatform.swapValue(newPlatform);
        });
    }
}
