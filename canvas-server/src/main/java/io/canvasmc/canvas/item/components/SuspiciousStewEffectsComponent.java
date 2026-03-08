package io.canvasmc.canvas.item.components;

import com.google.gson.JsonArray;
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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import org.jspecify.annotations.NonNull;

public class SuspiciousStewEffectsComponent extends ComponentType<SuspiciousStewEffects> {

    private static final Map<String, FieldInfo> ENTRY_FIELDS = Map.of(
        "id", FieldInfo.identifierField(ctx ->
            ctx.getSource().getServer().registryAccess()
                .lookupOrThrow(Registries.MOB_EFFECT)
                .keySet()),
        "duration", FieldInfo.intField()
    );

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "effects", FieldInfo.objectListField(ENTRY_FIELDS)
        );
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(
        final CommandContext<CommandSourceStack> context,
        final @NonNull SuggestionsBuilder builder
    ) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public SuspiciousStewEffects parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            JsonArray array = json.getAsJsonObject().getAsJsonArray("effects");

            List<SuspiciousStewEffects.Entry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                Identifier effectId = Identifier.parse(obj.get("id").getAsString());
                Holder<MobEffect> effect = MinecraftServer.getServer().registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT)
                    .get(effectId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + effectId));

                int duration = obj.has("duration") ? obj.get("duration").getAsInt() : 160;

                entries.add(new SuspiciousStewEffects.Entry(effect, duration));
            }

            return new SuspiciousStewEffects(entries);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<SuspiciousStewEffects> nms() {
        return DataComponents.SUSPICIOUS_STEW_EFFECTS;
    }
}
