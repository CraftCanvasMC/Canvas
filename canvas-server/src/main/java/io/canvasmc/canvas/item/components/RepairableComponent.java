package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Repairable;
import org.jspecify.annotations.NonNull;

public class RepairableComponent extends ComponentType<Repairable> {
    @Override
    public Repairable parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            final String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            RegistryAccess.Frozen registryAccess = MinecraftServer.getServer().registryAccess();
            JsonElement json = JsonParser.parseString(fixed);
            List<Holder<Item>> holders = new ArrayList<>();
            for (final JsonElement element : json.getAsJsonArray()) {
                holders.add(
                    registryAccess.lookupOrThrow(Registries.ITEM).get(Identifier.parse(element.getAsString())).orElseThrow()
                );
            }
            HolderSet<Item> items = HolderSet.direct(holders);
            return new Repairable(items);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, @NonNull SuggestionsBuilder builder) {
        return listOfSuggestions(
            context, builder, (newContext, newBuilder) ->
                SharedSuggestionProvider.suggestResource(MinecraftServer.getServer()
                    .registryAccess()
                    .lookupOrThrow(Registries.ITEM)
                    .keySet(), newBuilder)
        );
    }

    @Override
    public DataComponentType<Repairable> nms() {
        return DataComponents.REPAIRABLE;
    }
}
