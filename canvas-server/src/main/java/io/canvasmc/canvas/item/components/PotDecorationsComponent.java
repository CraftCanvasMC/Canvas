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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.PotDecorations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class PotDecorationsComponent extends ComponentType<PotDecorations> {
    private static Set<Identifier> POT_DECOR_VALID = null;

    private static @Nullable Item fromString(String str) {
        Identifier identifier = Identifier.tryParse(str);
        if (identifier == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        if (POT_DECOR_VALID == null) {
            final Registry<Item> itemRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.ITEM);
            POT_DECOR_VALID = itemRegistry.stream()
                .filter(item -> item.getDefaultInstance().is(ItemTags.DECORATED_POT_SHERDS))
                .map(itemRegistry::getKey)
                .collect(Collectors.toSet());
        }
        return Map.of(
            "back", FieldInfo.identifierField((context) -> POT_DECOR_VALID),
            "left", FieldInfo.identifierField((context) -> POT_DECOR_VALID),
            "right", FieldInfo.identifierField((context) -> POT_DECOR_VALID),
            "front", FieldInfo.identifierField((context) -> POT_DECOR_VALID)
        );
    }

    @Override
    public PotDecorations parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonObject json = JsonParser.parseString(fixed).getAsJsonObject();
            Item back = json.has("back") ? fromString(json.get("back").getAsString()) : null;
            Item left = json.has("left") ? fromString(json.get("left").getAsString()) : null;
            Item right = json.has("right") ? fromString(json.get("right").getAsString()) : null;
            Item front = json.has("front") ? fromString(json.get("front").getAsString()) : null;
            return new PotDecorations(Optional.ofNullable(back), Optional.ofNullable(left), Optional.ofNullable(right), Optional.ofNullable(front));
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<PotDecorations> nms() {
        return DataComponents.POT_DECORATIONS;
    }
}
