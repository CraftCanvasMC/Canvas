package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.SeededContainerLoot;
import org.jspecify.annotations.NonNull;

public class ContainerLootComponent extends ComponentType<SeededContainerLoot> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public SeededContainerLoot parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,:]\\s*)([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = GSON.fromJson(fixed, JsonElement.class);
            return SeededContainerLoot.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalArgumentException("Invalid value: " + msg));
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "loot_table", FieldInfo.identifierField((context) -> MinecraftServer.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier).collect(Collectors.toSet())),
            "seed", FieldInfo.intField()
        );
    }

    @Override
    public DataComponentType<SeededContainerLoot> nms() {
        return DataComponents.CONTAINER_LOOT;
    }
}
