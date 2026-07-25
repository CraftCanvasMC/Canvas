package io.canvasmc.canvas.subcommands.regiondata;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.commands.SubCommand;
import io.canvasmc.canvas.threadedregions.ScheduledHandleTickState;
import io.canvasmc.canvas.threadedregions.profiler.Session;
import io.canvasmc.canvas.threadedregions.profiler.platform.ProfilerPlatform;
import io.canvasmc.canvas.threadedregions.profiler.platform.SparkRegionProfiler;
import io.canvasmc.canvas.util.LockedReference;
import io.canvasmc.canvas.util.ReadWriteLockedReference;
import io.canvasmc.canvas.util.Util;
import io.canvasmc.canvas.util.command.CSEConsumer;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class RegionDataCommand implements SubCommand {
    public static final Pattern SPARK_PROFILER_START_REGEX = Pattern.compile("^spark\\s+profiler.*");

    private static final DynamicCommandExceptionType HANDLE_DOESNT_EXIST = new DynamicCommandExceptionType(
        (handle) -> Component.literal("No region was found at " + handle + ", is this area loaded?")
    );
    private static final SimpleCommandExceptionType NOT_SPRINTING = new SimpleCommandExceptionType(
        Component.literal("The target scheduling handle is not currently sprinting")
    );
    private static final SimpleCommandExceptionType NOT_PLAYING = new SimpleCommandExceptionType(
        Component.literal("The target scheduling handle is already paused")
    );
    private static final SimpleCommandExceptionType NOT_PAUSED = new SimpleCommandExceptionType(
        Component.literal("The target scheduling handle is already playing")
    );
    private static final SimpleCommandExceptionType NOT_ON_REGION = new SimpleCommandExceptionType(
        Component.literal("Not currently running on a region, and no target was specified")
    );

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
        // TODO - entity list(with filters), player list, region profile, tile entities, mob caps, scheduling info
        // TODO - should specify region for most of these, but if no region is specified it uses the current region
        //        should we include an ability to get data from all regions? mobcaps shouldnt, that's not safe, but
        //        players/entities can
        return base
            .then(literal("profiler")
                .then(literal("start"))
                .then(literal("stop"))
                .then(literal("check"))
                .then(literal("platform"))
            )
            .then(literal("list")
                .then(literal("players"))
                .then(literal("tiles"))
                .then(literal("entities"))
                .then(literal("mobcaps")
                    .then(literal("player")
                        .then(argument("target", EntityArgument.player())).executes(RegionDataCommand::executeGetPlayerMobCaps))
                    .then(literal("region")
                        .executes(RegionDataCommand::executeGetMobCaps)
                        .then(argument("world", DimensionArgument.dimension())
                            .then(argument("coords", ColumnPosArgument.columnPos()).executes(RegionDataCommand::executeGetMobCaps))))
                )
            )
            .then(literal("tick")
                .then(literal("rate").then(argument("rate", FloatArgumentType.floatArg(0.0F)).executes((context) -> {
                    TickRegionScheduler.setTickRate(FloatArgumentType.getFloat(context, "rate"));
                    return Command.SINGLE_SUCCESS;
                })))
                .then(literal("handle")
                    .then(addTickActions(literal("god-tick")))
                    .then(literal("region")
                        .then(argument("world", DimensionArgument.dimension())
                            .then(addTickActions(argument("coords", ColumnPosArgument.columnPos())))))
                )
            )
            .then(literal("info"));
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
        }, () -> currentPlatform.swapValue(newPlatform));
    }

    private static int executeGetMobCaps(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack css = ctx.getSource();
        schedule(getTickHandle(ctx, false), css, (rawHandle) -> {
            final TickRegions.ConcreteRegionTickHandle concrete = (TickRegions.ConcreteRegionTickHandle) rawHandle;
            MobCapUtils.sendMobCaps(css, Either.right(concrete.region.world.getCurrentWorldData()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int executeGetPlayerMobCaps(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack css = ctx.getSource();

        ServerPlayer target;

        try {
            target = EntityArgument.getPlayer(ctx, "target");
        } catch (final IllegalArgumentException ignored) {
            target = ctx.getSource().getPlayerOrException();
        }

        //noinspection CodeBlock2Expr
        target.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
            MobCapUtils.sendMobCaps(css, Either.left(entityPlayer));
        });

        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addTickActions(final ArgumentBuilder<CommandSourceStack, ?> base) {
        return base
            .then(literal("sprint").then(argument("ticks", LongArgumentType.longArg(0L, 100_000L)).executes((ctx) -> {
                final long ticksToSprint = LongArgumentType.getLong(ctx, "ticks");
                final Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> target = getTickHandle(ctx, true);
                final CommandSourceStack css = ctx.getSource();

                schedule(target, css, (handle) -> {
                    handle.getTickManager().postAction(new ScheduledHandleTickState.Action.StartSprinting(ticksToSprint));
                    css.sendSuccess(() -> Component.literal("Started sprint on \"" + getString(handle) + "\" for " + ticksToSprint + " ticks"), true);
                });

                return Command.SINGLE_SUCCESS;
            })))
            .then(literal("walk").executes((ctx) -> {
                final Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> target = getTickHandle(ctx, true);
                final CommandSourceStack css = ctx.getSource();

                schedule(target, css, (handle) -> {
                    if (!handle.getTickManager().isSprinting()) {
                        throw NOT_SPRINTING.create();
                    }
                    handle.getTickManager().postAction(new ScheduledHandleTickState.Action.StopSprinting());
                    css.sendSuccess(() -> Component.literal("Stopped sprint for \"" + getString(handle) + "\""), true);
                });

                return Command.SINGLE_SUCCESS;
            }))
            .then(literal("pause").executes((ctx) -> {
                final Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> target = getTickHandle(ctx, true);
                final CommandSourceStack css = ctx.getSource();

                schedule(target, css, (handle) -> {
                    if (!handle.getTickManager().doesRunGameElements()) {
                        throw NOT_PLAYING.create();
                    }
                    handle.getTickManager().postAction(new ScheduledHandleTickState.Action.Pause());
                    css.sendSuccess(() -> Component.literal("Paused running game elements for \"" + getString(handle) + "\""), true);
                });

                return Command.SINGLE_SUCCESS;
            }))
            .then(literal("play").executes((ctx) -> {
                final Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> target = getTickHandle(ctx, true);
                final CommandSourceStack css = ctx.getSource();

                schedule(target, css, (handle) -> {
                    if (handle.getTickManager().doesRunGameElements()) {
                        throw NOT_PAUSED.create();
                    }
                    handle.getTickManager().postAction(new ScheduledHandleTickState.Action.Play());
                    css.sendSuccess(() -> Component.literal("Resumed running game elements for \"" + getString(handle) + "\""), true);
                });

                return Command.SINGLE_SUCCESS;
            }));
    }

    private static String getString(final TickRegionScheduler.RegionScheduleHandle handle) {
        if (handle instanceof RegionizedServer.GlobalTickTickHandle) {
            return "god-tick";
        }
        else if (handle instanceof TickRegions.ConcreteRegionTickHandle concrete) {
            final ChunkPos center = concrete.region.region.getCenterChunk();
            final ServerLevel world = concrete.region.world;

            return world.dimension().identifier().toShortString() + Objects.requireNonNull(center, "Region must contain center");
        }
        throw new UnsupportedOperationException("Unknown handle: " + handle.getClass().getName());
    }

    private static void schedule(
        final Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> target,
        final CommandSourceStack css,
        final CSEConsumer<TickRegionScheduler.RegionScheduleHandle> action
    ) throws CommandSyntaxException {
        final TickRegionScheduler.RegionScheduleHandle handle;

        try {
            handle = Util.getEitherOrNull(
                target.mapRight((pair) -> {
                    final ColumnPos blockPos = pair.getFirst();
                    final int chunkX = blockPos.x() >> 4;
                    final int chunkZ = blockPos.z() >> 4;

                    final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>
                        region = pair.getSecond().regioniser.getRegionAtSynchronised(chunkX, chunkZ);

                    if (region == null) {
                        final CommandSyntaxException cse = HANDLE_DOESNT_EXIST.create(new ColumnPos(chunkX << 4, chunkZ << 4));

                        css.sendFailure(Component.literal(cse.getMessage()));
                        throw new IllegalArgumentException(cse);
                    }

                    return region.getData().getRegionSchedulingHandle();
                }).mapLeft(TickRegionScheduler.RegionScheduleHandle.class::cast)
            );
        } catch (final IllegalArgumentException ignored) {

            // the global tick would've passed if specified, so this means
            // the region didn't exist at the coords, which feedback was already
            // sent so we don't actually care

            return;
        }

        // run the action on the handle
        action.act(handle);
    }

    private static Either<RegionizedServer.GlobalTickTickHandle, Pair<ColumnPos, ServerLevel>> getTickHandle(
        final CommandContext<CommandSourceStack> ctx, final boolean globalTickAvailable
    ) throws CommandSyntaxException {

        // we essentially try to parse the region first, and if
        // the arguments for the world or column pos don't exist,
        // we can guess that we are targeting the god tick

        try {
            final ServerLevel world = DimensionArgument.getDimension(ctx, "world");
            final ColumnPos coords = ColumnPosArgument.getColumnPos(ctx, "coords");

            // if we made it this far, the arguments are valid
            return Either.right(Pair.of(coords, world));
        } catch (final Throwable thrown) {
            if (thrown instanceof CommandSyntaxException cse) {
                throw cse;
            }
            else if (thrown instanceof IllegalArgumentException) {

                // the argument doesn't exist, meaning it probably
                // is targeting the god tick if possible

                if (globalTickAvailable) {
                    return Either.left((RegionizedServer.GlobalTickTickHandle) RegionizedServer.getGlobalTickData());
                }

                // if global tick is unavailable, see if we can get the current region
                // as a fallback, and if it's not present, throw

                final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>
                    currentRegion = TickRegionScheduler.getCurrentRegion();
                if (currentRegion != null && !currentRegion.hasNoAliveSections()) {
                    final ChunkPos center = currentRegion.getCenterChunk();

                    return Either.right(Pair.of(new ColumnPos(center.x(), center.z()), currentRegion.regioniser.world));
                }

                // if the current region is null or all the sections are dead, we should just throw
                throw NOT_ON_REGION.create();
            }
            throw thrown;
        }
    }
}
