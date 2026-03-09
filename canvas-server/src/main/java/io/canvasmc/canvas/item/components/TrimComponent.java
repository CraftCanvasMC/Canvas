package io.canvasmc.canvas.item.components;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jspecify.annotations.NonNull;

public class TrimComponent extends ComponentType<ArmorTrim> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        final RegistryAccess.Frozen registryAccess = MinecraftServer.getServer().registryAccess();
        return Map.of(
            "material", FieldInfo.identifierField((context) -> registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL).keySet()),
            "pattern", FieldInfo.identifierField((context) -> registryAccess.lookupOrThrow(Registries.TRIM_PATTERN).keySet())
        );
    }

    @Override
    public ArmorTrim parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            final RegistryAccess.Frozen registryAccess = MinecraftServer.getServer().registryAccess();
            JsonObject json = JsonParser.parseString(fixed).getAsJsonObject();
            return new ArmorTrim(
                registryAccess.lookupOrThrow(Registries.TRIM_MATERIAL).get(Identifier.parse(json.get("material").getAsString())).orElseThrow(() -> new NoSuchElementException("Material not found")),
                registryAccess.lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.parse(json.get("pattern").getAsString())).orElseThrow(() -> new NoSuchElementException("Pattern not found"))
            );
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<ArmorTrim> nms() {
        return DataComponents.TRIM;
    }
}
