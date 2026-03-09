package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import org.jspecify.annotations.NonNull;

public class FireworksComponent extends ComponentType<Fireworks> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "flight_duration", FieldInfo.intField(),
            "explosions", FieldInfo.listField(FieldInfo.objectField(FireworkExplosionComponent.EXPLOSION_FIELDS))
        );
    }

    @Override
    public Fireworks parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonObject json = JsonParser.parseString(fixed).getAsJsonObject();
            int duration = json.has("flight_duration") ? json.get("flight_duration").getAsInt() : 0;
            if (duration > 255) {
                throw new IllegalArgumentException("Duration must be less than 255");
            }
            List<FireworkExplosion> explosions = new ArrayList<>();
            if (json.has("explosions")) {
                for (final JsonElement jE : json.get("explosions").getAsJsonArray()) {
                    if (!(jE instanceof JsonObject object))
                        throw new IllegalArgumentException("Element in 'explosions' not json object");
                    explosions.add(FireworkExplosionComponent.getExplosionFromJson(object));
                }
            }
            return new Fireworks(duration, explosions);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<Fireworks> nms() {
        return DataComponents.FIREWORKS;
    }
}
