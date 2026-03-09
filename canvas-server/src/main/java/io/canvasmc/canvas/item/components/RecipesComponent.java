package io.canvasmc.canvas.item.components;

import com.google.gson.JsonArray;
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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.NonNull;

public class RecipesComponent extends ComponentType<List<ResourceKey<Recipe<?>>>> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return listOfSuggestions(context, builder, (newContext, newBuilder) -> {
            List<Identifier> ids = new ArrayList<>();
            for (final ResourceKey<Recipe<?>> key : MinecraftServer.getServer().getRecipeManager().recipes.byKey.keySet()) {
                ids.add(key.identifier());
            }
            return SharedSuggestionProvider.suggestResource(ids, newBuilder);
        });
    }

    @Override
    public List<ResourceKey<Recipe<?>>> parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = "{\"list\":" + raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            ) + "}";
            JsonArray json = JsonParser.parseString(fixed).getAsJsonObject().get("list").getAsJsonArray();
            List<ResourceKey<Recipe<?>>> resourceKeys = new ArrayList<>();
            for (final JsonElement jE : json) {
                resourceKeys.add(ResourceKey.create(Registries.RECIPE, Identifier.parse(jE.getAsString())));
            }
            return resourceKeys;
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<List<ResourceKey<Recipe<?>>>> nms() {
        return DataComponents.RECIPES;
    }
}
