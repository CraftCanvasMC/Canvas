package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.item.ComponentType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
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
            JsonElement json = GSON.fromJson(fixed, JsonElement.class);
            return ItemLore.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalArgumentException("Invalid lore: " + msg));
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, @NonNull SuggestionsBuilder builder) {
        return listOfSuggestions(context, builder, (newContext, newBuilder) -> componentParser.parseForSuggestions(newBuilder));
    }

    @Override
    public DataComponentType<ItemLore> nms() {
        return DataComponents.LORE;
    }
}
