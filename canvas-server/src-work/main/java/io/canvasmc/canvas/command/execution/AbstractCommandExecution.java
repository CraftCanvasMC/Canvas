package io.canvasmc.canvas.command.execution;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.canvasmc.canvas.util.CanonicalReference;
import io.canvasmc.canvas.util.LockedReference;
import io.canvasmc.canvas.util.command.AbstractCommandFunction;
import io.canvasmc.canvas.util.command.CommandIntSupplier;
import io.canvasmc.canvas.util.command.CommandRunnable;
import io.canvasmc.canvas.util.command.TriCommandFunction;
import io.papermc.paper.threadedregions.RegionizedServer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class AbstractCommandExecution<R, E extends Entity> {

    protected static final DynamicCommandExceptionType INVALID_EXECUTION = new DynamicCommandExceptionType(
        type -> Component.literal("Invalid execution arguments provided. Forgot to provide \"" + type + "\" argument")
    );
    protected static final DynamicCommandExceptionType ALREADY_PROVIDED = new DynamicCommandExceptionType(
        type -> Component.literal("Already provided \"" + type + "\" argument to execution constructor")
    );

    public static final DynamicCommandExceptionType CANNOT_DO_X_CROSS_REGION = new DynamicCommandExceptionType(
        action -> Component.literal("Cannot " + action + " cross-region")
    );

    final LockedReference<R> dataInstance;
    final CanonicalReference<TriCommandFunction<R, List<? extends E>, CommandSourceStack>> complete;
    final CanonicalReference<AbstractCommandFunction<E, Function<R, R>>> commandAction;
    final CanonicalReference<List<? extends E>> targets;

    AbstractCommandExecution(final R dataInstance) {
        this.dataInstance = new LockedReference<>(dataInstance);
        this.complete = new CanonicalReference<>();
        this.commandAction = new CanonicalReference<>();
        this.targets = new CanonicalReference<>();
    }

    public static int executeAtPosWithRadius(final @NonNull CommandIntSupplier action, final @NonNull Vec3 pos, final int radius, final Level level, final CommandSourceStack css) {
        return executeAtPosWithRadius(action, BlockPos.containing(pos), radius, level, css);
    }

    public static int executeAtPosWithRadius(final CommandRunnable action, final @NonNull Vec3 pos, final int radius, final Level level, final CommandSourceStack css) {
        return executeAtPosWithRadius(action, BlockPos.containing(pos), radius, level, css);
    }

    public static int executeAtPosWithRadius(final CommandRunnable action, final @NonNull BlockPos pos, final int radius, final @NonNull Level level, final CommandSourceStack css) {
        return executeAtPosWithRadius(() -> {
            action.act();
            return Command.SINGLE_SUCCESS;
        }, pos, radius, level, css);
    }

    public static int executeAtPosWithRadius(final @NonNull CommandIntSupplier action, final @NonNull BlockPos pos, final int radius, final @NonNull Level level, final CommandSourceStack css) {
        level.canvas$loadOrRunAtChunksAsync(
            pos, radius, Priority.NORMAL, () -> {
                try {
                    action.act();
                } catch (final CommandSyntaxException cse) {
                    css.sendFailure(Component.literal(cse.getMessage()));
                }
            }
        );
        return Command.SINGLE_SUCCESS;
    }

    public static int executeAtPos(final CommandRunnable action, final @NonNull Vec3 pos, final Level level, final CommandSourceStack css) {
        return executeAtPos(action, BlockPos.containing(pos), level, css);
    }

    public static int executeAtPos(final CommandIntSupplier action, final @NonNull Vec3 pos, final Level level, final CommandSourceStack css) {
        return executeAtPos(action, BlockPos.containing(pos), level, css);
    }

    public static int executeAtPos(final CommandRunnable action, final @NonNull BlockPos pos, final Level level, final CommandSourceStack css) {
        return executeAtPos(() -> {
            action.act();
            return Command.SINGLE_SUCCESS;
        }, pos, level, css);
    }

    public static int executeAtPos(final CommandIntSupplier action, final @NonNull BlockPos pos, final Level level, final CommandSourceStack css) {
        RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(
            (ServerLevel) level,
            pos.getX() >> 4,
            pos.getZ() >> 4,
            () -> {
                try {
                    action.act();
                } catch (final CommandSyntaxException cse) {
                    css.sendFailure(Component.literal(cse.getMessage()));
                }
            }
        );
        return Command.SINGLE_SUCCESS;
    }

    public static int executeOnGlobal(final CommandRunnable action, final CommandSourceStack css) {
        return executeOnGlobal(() -> {
            action.act();
            return Command.SINGLE_SUCCESS;
        }, css);
    }

    public static int executeOnGlobal(final CommandIntSupplier action, final CommandSourceStack css) {
        RegionizedServer.getInstance().scheduleToOrExecute(() -> {
            try {
                action.act();
            } catch (final CommandSyntaxException cse) {
                css.sendFailure(Component.literal(cse.getMessage()));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    @Contract("_ -> new")
    public static <R, E extends Entity> @NonNull AbstractCommandExecution<R, E> _abstract(final R _default) {
        return new AbstractCommandExecution<>(_default);
    }

    @Contract("_ -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Boolean, E> _boolean(final boolean _default) {
        return _abstract(_default);
    }

    @Contract(" -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Void, E> _void() {
        return _abstract(null);
    }

    @Contract(" -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Integer, E> _int() {
        return _abstract(0);
    }

    @Contract(" -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Long, E> _long() {
        return _abstract(0L);
    }

    @Contract(" -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Double, E> _double() {
        return _abstract(0.0D);
    }

    @Contract(" -> new")
    public static <E extends Entity> @NonNull AbstractCommandExecution<Float, E> _float() {
        return _abstract(0.0F);
    }

    public AbstractCommandExecution<R, E> noCompletion() throws CommandSyntaxException {
        return onComplete((_, _, _) -> Command.SINGLE_SUCCESS);
    }

    public AbstractCommandExecution<R, E> onComplete(final TriCommandFunction<R, List<? extends E>, CommandSourceStack> complete) throws CommandSyntaxException {
        if (this.complete.isSet()) {
            throw ALREADY_PROVIDED.create("on complete");
        }
        this.complete.setValue(complete);
        return this;
    }

    public AbstractCommandExecution<R, E> executes(final AbstractCommandFunction<E, Function<R, R>> action) throws CommandSyntaxException {
        if (this.commandAction.isSet()) {
            throw ALREADY_PROVIDED.create("command action");
        }
        this.commandAction.setValue(action);
        return this;
    }

    public AbstractCommandExecution<R, E> with(final E singular) throws CommandSyntaxException {
        return with(List.of(singular));
    }

    public AbstractCommandExecution<R, E> with(final @NonNull Stream<? extends E> targetsStream) throws CommandSyntaxException {
        return with(targetsStream.toList());
    }

    public AbstractCommandExecution<R, E> with(final Collection<? extends E> targets) throws CommandSyntaxException {
        if (this.targets.isSet()) {
            throw ALREADY_PROVIDED.create("targets");
        }
        // stream to list so we copy it so it's immutable
        this.targets.setValue(targets.stream().toList());
        return this;
    }

    public void start(final CommandSourceStack sourceStack) throws CommandSyntaxException {
        if (!this.complete.isSet()) {
            throw INVALID_EXECUTION.create("on complete");
        }
        if (!this.commandAction.isSet()) {
            throw INVALID_EXECUTION.create("on complete");
        }
        if (!this.targets.isSet()) {
            throw INVALID_EXECUTION.create("targets");
        }

        final List<? extends E> targets = this.targets.value();
        final AtomicInteger count = new AtomicInteger(targets.size());
        final Consumer<E> action = constructAction(sourceStack, count, targets);

        // handle the special case where a command could give 0 valid targets
        if (count.get() == 0) {
            try {
                this.complete.value().act(this.dataInstance.getValue(), targets, sourceStack);
            } catch (final CommandSyntaxException cse1) {
                sourceStack.sendFailure(Component.literal(cse1.getMessage()));
            }
            return;
        }

        // iterate over all targets now and schedule or execute
        for (final E selected : targets) {
            if (TickThread.isTickThreadFor(selected)) {
                action.accept(selected);
            }
            else {
                final Consumer<Entity> retired = (_) -> {
                    // entity is retired, so we just decrement
                    // with no action being executed
                    if (count.decrementAndGet() == 0) {
                        try {
                            this.complete.value().act(this.dataInstance.getValue(), targets, sourceStack);
                        } catch (final CommandSyntaxException cse1) {
                            sourceStack.sendFailure(Component.literal(cse1.getMessage()));
                        }
                    }
                };
                if (!selected.getBukkitEntity().taskScheduler.schedule(
                    action, retired, 1L
                )) {
                    // entity is retired already
                    retired.accept(selected);
                }
            }
        }
    }

    private @NonNull Consumer<E> constructAction(final CommandSourceStack sourceStack, final AtomicInteger count, final List<? extends E> targets) {
        final AtomicBoolean hasFailed = new AtomicBoolean(false);

        return (entity) -> {

            // if we failed, don't do anything
            if (hasFailed.get()) {
                return;
            }

            try {
                final Function<R, R> dataModifier = this.commandAction.value().act(entity);
                this.dataInstance.swapValue(dataModifier::apply);
            } catch (final Throwable uncaught) {
                hasFailed.set(true);
                // if syntax exception, we should tell the source
                if (uncaught instanceof CommandSyntaxException cse) {
                    sourceStack.sendFailure(Component.literal(cse.getMessage()));
                    return;
                }

                // realistically, this should kill the server if not a CSE...in theory
                // but only if this was scheduled. if it wasn't this will do nothing so we
                // need to set "has failed" anyway or else this may continue

                throw new RuntimeException("Uncaught exception when executing ACE constructed command", uncaught);
            } finally {
                if (count.decrementAndGet() == 0 && !hasFailed.get()) {
                    try {
                        this.complete.value().act(this.dataInstance.getValue(), targets, sourceStack);
                    } catch (final CommandSyntaxException cse1) {
                        sourceStack.sendFailure(Component.literal(cse1.getMessage()));
                    }
                }
            }
        };
    }
}
