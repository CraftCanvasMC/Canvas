package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jspecify.annotations.NonNull;

public class BlockStateComponent extends ComponentType<BlockItemStateProperties> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "properties", FieldInfo.listField(FieldInfo.objectField(Map.of(
                "key", FieldInfo.stringField(),
                "value", FieldInfo.stringField()
            )))
        );
    }

    @Override
    public BlockItemStateProperties parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonObject json = GSON.fromJson(fixed, JsonObject.class);
            Map<String, String> properties = new HashMap<>();
            for (final JsonElement jE : json.getAsJsonArray("properties")) {
                JsonObject rawProperty = jE.getAsJsonObject();
                String key = rawProperty.get("key").getAsString();
                String value = rawProperty.get("value").getAsString();
                properties.put(key, value);
            }
            return new BlockItemStateProperties(properties);
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<BlockItemStateProperties> nms() {
        return DataComponents.BLOCK_STATE;
    }
}
