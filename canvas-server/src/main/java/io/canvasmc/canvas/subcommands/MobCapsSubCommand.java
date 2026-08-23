package io.canvasmc.canvas.subcommands;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.commands.SubCommand;
import io.canvasmc.canvas.threadedregions.commands.AbstractCommandExecution;
import io.canvasmc.canvas.util.StringSuggestionProvider;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

// note: CraftServer#spawnCategoryLimit is an O2I hashmap, which just replaces
//       the value in the array directly, so this IS thread-safe, since we aren't
//       inserting new mob categories
public class MobCapsSubCommand implements SubCommand {
    private static final DynamicCommandExceptionType INVALID_CATEGORY = new DynamicCommandExceptionType(
        (obj) -> Component.literal("Unknown category by name of \"" + obj + "\"")
    );
    private static final SimpleCommandExceptionType INVALID_FOR_LIMITS = new SimpleCommandExceptionType(
        Component.literal("This category is invalid for limits")
    );

    private static final String[] ARGS = Arrays.stream(SpawnCategory.values())
        .filter((sc) -> !sc.equals(SpawnCategory.MISC))
        .map(Enum::name)
        .map(String::toLowerCase)
        .collect(Collectors.toSet())
        .toArray(new String[0]);
    private static final ImmutableMap<String, SpawnCategory> NAME2CATEGORY;

    static {
        final ImmutableMap.Builder<String, SpawnCategory> builder = new ImmutableMap.Builder<>();

        for (final String arg : ARGS) {
            final String upper = arg.toUpperCase();

            builder.put(upper, SpawnCategory.valueOf(upper));
        }

        NAME2CATEGORY = builder.build();
    }

    @Override
    public String getDescription() {
        return "Allows modifying the mob caps of the server transiently";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(
        final LiteralArgumentBuilder<CommandSourceStack> base,
        final CommandBuildContext buildContext
    ) {
        return base
            .then(literal("get")
                      .then(argument("category", StringArgumentType.word())
                                .suggests(new StringSuggestionProvider(ARGS))
                                .executes(MobCapsSubCommand::getOperation)))
            .then(literal("set")
                      .then(argument("category", StringArgumentType.word())
                                .suggests(new StringSuggestionProvider(ARGS))
                                .then(argument("value", IntegerArgumentType.integer(0))
                                          .executes(MobCapsSubCommand::setOperation))))
            .then(literal("reset")
                      .then(argument("category", StringArgumentType.word())
                                .suggests(new StringSuggestionProvider(ARGS))
                                .executes(MobCapsSubCommand::resetOperation)));
    }

    @Override
    public String getName() {
        return "mobcaps";
    }

    private static int resetOperation(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack css = ctx.getSource();

        return AbstractCommandExecution.executeOnGlobal(
            () -> {
                final SpawnCategory category = getCategory(ctx);
                final CraftServer craftServer = MinecraftServer.getServer().server;
                //noinspection removal - holy hell ew
                final int resetValue = craftServer.spigot()
                    .getBukkitConfig()
                    .getInt(CraftSpawnCategory.getConfigNameSpawnLimit(category));

                // this **should** be thread-safe
                craftServer.spawnCategoryLimit.put(category, resetValue);
                css.sendSuccess(
                    () -> Component.literal("Reset limit of \"" + category + "\" to default value, " + resetValue),
                    false
                );

                return Command.SINGLE_SUCCESS;
            }, css
        );
    }

    private static int setOperation(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack css = ctx.getSource();

        return AbstractCommandExecution.executeOnGlobal(
            () -> {
                final SpawnCategory category = getCategory(ctx);
                final int value = IntegerArgumentType.getInteger(ctx, "value");

                // someone will try this istg
                if (!CraftSpawnCategory.isValidForLimits(category)) {
                    throw INVALID_FOR_LIMITS.create();
                }

                // this **should** be thread-safe
                MinecraftServer.getServer().server.spawnCategoryLimit.put(category, value);
                css.sendSuccess(
                    () -> Component.literal("Set limit of \"" + category + "\" to " + value),
                    false
                );

                return Command.SINGLE_SUCCESS;
            }, css
        );
    }

    private static int getOperation(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack css = ctx.getSource();

        return AbstractCommandExecution.executeOnGlobal(
            () -> {
                final SpawnCategory category = getCategory(ctx);
                final int value = MinecraftServer.getServer().server.getSpawnLimitUnsafe(category);

                css.sendSuccess(
                    () -> Component.literal("Category by name of \"" + category + "\" has a limit of " + value),
                    false
                );

                return Command.SINGLE_SUCCESS;
            }, css
        );
    }

    private static SpawnCategory getCategory(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final String raw = StringArgumentType.getString(ctx, "category");
        final String upper = raw.toUpperCase();
        final SpawnCategory parsed = NAME2CATEGORY.get(upper);

        if (parsed == null) {
            throw INVALID_CATEGORY.create(raw);
        }

        return parsed;
    }
}
