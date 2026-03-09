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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.FireworkExplosion;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class FireworkExplosionComponent extends ComponentType<FireworkExplosion> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public FireworkExplosion parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonObject json = JsonParser.parseString(fixed).getAsJsonObject();
            return getExplosionFromJson(json);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Contract("_ -> new")
    protected static @NonNull FireworkExplosion getExplosionFromJson(final @NonNull JsonObject json) {
        FireworkExplosion.Shape shape = FireworkExplosion.Shape.valueOf(json.get("shape").getAsString().toUpperCase());
        IntList colors = new IntArrayList();
        if (json.has("colors")) {
            for (final JsonElement jE : json.get("colors").getAsJsonArray()) {
                colors.add(jE.getAsInt());
            }
        }
        IntList fadeColors = new IntArrayList();
        if (json.has("fade_colors")) {
            for (final JsonElement jE : json.get("fade_colors").getAsJsonArray()) {
                fadeColors.add(jE.getAsInt());
            }
        }
        boolean hasTrail = json.has("has_trail") && json.getAsJsonPrimitive("has_trail").getAsBoolean();
        boolean hasTwinkle = json.has("has_twinkle") && json.getAsJsonPrimitive("has_twinkle").getAsBoolean();
        return new FireworkExplosion(shape, colors, fadeColors, hasTrail, hasTwinkle);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "shape", FieldInfo.stringField(Arrays.stream(FireworkExplosion.Shape.values()).map(Enum::toString).distinct().toArray(String[]::new)),
            "colors", FieldInfo.listField(FieldInfo.intField()),
            "fade_colors", FieldInfo.listField(FieldInfo.intField()),
            "has_trail", FieldInfo.bool(),
            "has_twinkle", FieldInfo.bool()
        );
    }

    @Override
    public DataComponentType<FireworkExplosion> nms() {
        return DataComponents.FIREWORK_EXPLOSION;
    }
}
