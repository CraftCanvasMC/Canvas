package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.item.ComponentType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;

import static net.minecraft.commands.arguments.ComponentArgument.ERROR_INVALID_COMPONENT;

public class ItemLoreComponent extends ComponentType<ItemLore> {
    private final CommandArgumentParser<Component> componentParser;

    public ItemLoreComponent(@NonNull CommandBuildContext buildContext) {
        final CommandArgumentParser<Tag> tagParser = SnbtGrammar.createParser(NbtOps.INSTANCE);
        this.componentParser = tagParser.withCodec(
            buildContext.createSerializationContext(NbtOps.INSTANCE),
            tagParser,
            ComponentSerialization.CODEC,
            ERROR_INVALID_COMPONENT
        );
    }

    @Override
    public ItemLore parse(final @NonNull String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            return ItemLore.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalArgumentException("Invalid lore: " + msg));
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, @NonNull SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().stripTrailing();

        if (remaining.isEmpty()) {
            return SharedSuggestionProvider.suggest(List.of("["), builder);
        }

        if (remaining.endsWith("]")) {
            return builder.buildFuture();
        }

        if (remaining.matches(".*}\\s*")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of(",", "]"), offset);
        }

        int entryStart = Math.max(remaining.lastIndexOf('['), remaining.lastIndexOf(',')) + 1;
        String afterSep = remaining.substring(entryStart);
        int whitespace = afterSep.length() - afterSep.stripLeading().length();
        int absoluteEntryStart = builder.getStart() + entryStart + whitespace;

        return componentParser.parseForSuggestions(builder.createOffset(absoluteEntryStart));
    }

    @Override
    public DataComponentType<ItemLore> nms() {
        return DataComponents.LORE;
    }
}
