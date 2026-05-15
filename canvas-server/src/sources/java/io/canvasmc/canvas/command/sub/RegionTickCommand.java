package io.canvasmc.canvas.command.sub;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.tick.ScheduledHandleTickState;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.SharedSuggestionProvider.matchesSubStr;

@NullMarked
public class RegionTickCommand implements Command {
    private static final SimpleCommandExceptionType TOO_MANY_ARGUMENTS = new SimpleCommandExceptionType(
        Component.literal("Too many arguments provided for handle definition")
    );
    private static final SimpleCommandExceptionType UNKNOWN_ARGUMENTS = new SimpleCommandExceptionType(
        Component.literal("Unknown argument(s) provided for handle definition")
    );
    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(
        Component.literal("You must be a player to execute with local arguments")
    );
    private static final SimpleCommandExceptionType NO_REGION_EXISTS = new SimpleCommandExceptionType(
        Component.literal("No region exists at the specified block coordinates")
    );

    private static void postActionTo(String arg, @Nullable ServerPlayer player, Consumer<TickRegionScheduler.RegionScheduleHandle> action) throws CommandSyntaxException {
        String[] parts = arg.split("\\s+");
        if (parts.length == 0) throw UNKNOWN_ARGUMENTS.create();
        String first = parts[0].toLowerCase();
        switch (first) {
            case "global" -> {
                action.accept(RegionizedServer.getGlobalTickData());
                return;
            }
            case "server" -> {
                action.accept(RegionizedServer.getGlobalTickData());
                for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                    level.regioniser.computeForAllRegionsUnsynchronised((region) -> action.accept(region.getData().tickHandle));
                }
                return;
            }
            default -> {
                if (parts.length == 2) {
                    String second = parts[1];
                    if (player == null) {
                        throw MUST_BE_PLAYER.create();
                    }
                    int chunkX = (first.equalsIgnoreCase("~") ? player.blockPosition.getX() : Integer.parseInt(first)) >> 4;
                    int chunkZ = (second.equalsIgnoreCase("~") ? player.blockPosition.getZ() : Integer.parseInt(second)) >> 4;
                    ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                        player.level().regioniser.getRegionAtUnsynchronised(chunkX, chunkZ);
                    if (region == null) {
                        throw NO_REGION_EXISTS.create();
                    }
                    action.accept(region.getData().tickHandle);
                    return;
                }
                else if (parts.length > 2) {
                    throw TOO_MANY_ARGUMENTS.create();
                }
            }
        }
        throw UNKNOWN_ARGUMENTS.create();
    }

    @Override
    public String getName() {
        return "tick";
    }

    @Override
    public @Nullable String getDescription() {
        return "Allows modifying the server-wide, or schedule handle specific tick states";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base) {
        return base
            .then(literal("rate").then(argument("rate", FloatArgumentType.floatArg(0.0F)).executes((context) -> {
                float newTickRate = context.getArgument("rate", Float.class);
                TickRegionScheduler.setTickRate(newTickRate);
                return 0;
            })))
            // we cap it at 100k ticks to sprint, because yes... people do this... and it causes issues
            .then(literal("sprint").then(argument("ticks", LongArgumentType.longArg(0, 100_000L))
                .then(argument("handle", StringArgumentType.greedyString()).suggests(new HandleSuggestionProvider()).executes((context) -> {
                    long ticksToSprint = context.getArgument("ticks", Long.class);
                    postActionTo(context.getArgument("handle", String.class), context.getSource().getPlayer(), (scheduleHandle) -> {
                        scheduleHandle.getTickManager().postAction(new ScheduledHandleTickState.Action.StartSprinting(ticksToSprint));
                    });
                    context.getSource().sendSuccess(() -> Component.literal(String.format("Posted to marked schedule handles to sprint for %s ticks", ticksToSprint)), true);
                    return 0;
                }))
            ))
            .then(literal("walk")
                .then(argument("handle", StringArgumentType.greedyString()).suggests(new HandleSuggestionProvider()).executes((context) -> {
                    postActionTo(context.getArgument("handle", String.class), context.getSource().getPlayer(), (scheduleHandle) -> {
                        if (scheduleHandle.getTickManager().isSprinting()) {
                            scheduleHandle.getTickManager().postAction(new ScheduledHandleTickState.Action.StopSprinting());
                        }
                    });
                    context.getSource().sendSuccess(() -> Component.literal("Posted to marked schedule handles to stop sprinting"), true);
                    return 0;
                })))
            .then(literal("pause")
                .then(argument("handle", StringArgumentType.greedyString()).suggests(new HandleSuggestionProvider()).executes((context) -> {
                    postActionTo(context.getArgument("handle", String.class), context.getSource().getPlayer(), (scheduleHandle) -> {
                        scheduleHandle.getTickManager().postAction(new ScheduledHandleTickState.Action.Pause());
                    });
                    context.getSource().sendSuccess(() -> Component.literal("Posted to marked schedule handles to pause running game elements"), true);
                    return 0;
                })))
            .then(literal("play")
                .then(argument("handle", StringArgumentType.greedyString()).suggests(new HandleSuggestionProvider()).executes((context) -> {
                    postActionTo(context.getArgument("handle", String.class), context.getSource().getPlayer(), (scheduleHandle) -> {
                        scheduleHandle.getTickManager().postAction(new ScheduledHandleTickState.Action.Play());
                    });
                    context.getSource().sendSuccess(() -> Component.literal("Posted to marked schedule handles to run game elements"), true);
                    return 0;
                })));
    }

    private static final class HandleSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        public static final SimpleCommandExceptionType ERROR_NOT_COMPLETE = new SimpleCommandExceptionType(Component.translatable("argument.pos2d.incomplete"));

        @Contract(pure = true)
        @Override
        public CompletableFuture<Suggestions> getSuggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) throws CommandSyntaxException {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String id : List.of("global", "server")) {
                if (remaining.isEmpty()
                    || (matchesSubStr(remaining, id)))
                    builder.suggest(id);
            }
            Collection<SharedSuggestionProvider.TextCoordinates> coordinates;
            if (!remaining.isEmpty() && remaining.charAt(0) == '^') {
                coordinates = Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_LOCAL);
            }
            else {
                coordinates = context.getSource().getRelevantCoordinates();
            }

            List<String> list = gatherCoordinates(remaining, coordinates);
            String string = builder.getRemaining().toLowerCase(Locale.ROOT);

            for (String string1 : list) {
                if (matchesSubStr(string, string1.toLowerCase(Locale.ROOT))) {
                    builder.suggest(string1);
                }
            }
            return builder.buildFuture();
        }

        private List<String> gatherCoordinates(final String remaining, final Collection<SharedSuggestionProvider.TextCoordinates> coordinates) {
            final Predicate<String> validator = Commands.createValidator(this::parse);
            List<String> list = Lists.newArrayList();
            if (Strings.isNullOrEmpty(remaining)) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates : coordinates) {
                    String string = textCoordinates.x + " " + textCoordinates.z;
                    if (validator.test(string)) {
                        list.add(textCoordinates.x);
                        list.add(string);
                    }
                }
            }
            else {
                String[] parts = remaining.split(" ");
                if (parts.length == 1) {
                    for (SharedSuggestionProvider.TextCoordinates textCoordinates1 : coordinates) {
                        String string1 = parts[0] + " " + textCoordinates1.z;
                        if (validator.test(string1)) {
                            list.add(string1);
                        }
                    }
                }
            }
            return list;
        }

        public void parse(StringReader reader) throws CommandSyntaxException {
            int cursor = reader.getCursor();
            if (!reader.canRead()) {
                throw ERROR_NOT_COMPLETE.createWithContext(reader);
            }
            else {
                WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(reader);
                if (reader.canRead() && reader.peek() == ' ') {
                    reader.skip();
                    WorldCoordinate worldCoordinate1 = WorldCoordinate.parseInt(reader);
                    new WorldCoordinates(worldCoordinate, new WorldCoordinate(true, 0.0), worldCoordinate1);
                }
                else {
                    reader.setCursor(cursor);
                    throw ERROR_NOT_COMPLETE.createWithContext(reader);
                }
            }
        }

    }
}
