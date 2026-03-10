package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.NonNull;

public class PotionContentsComponent extends ComponentType<PotionContents> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "potion", FieldInfo.identifierField((context) -> MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.POTION).keySet()),
            "custom_color", FieldInfo.intField(),
            "custom_name", FieldInfo.stringField(),
            "custom_effects", FieldInfo.listField(FieldInfo.objectField(Map.of(
                "effect", FieldInfo.identifierField((context) -> MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.MOB_EFFECT).keySet()),
                "duration", FieldInfo.intField(),
                "amplifier", FieldInfo.intField(),
                "ambient", FieldInfo.bool(),
                "show_particles", FieldInfo.bool(),
                "show_icon", FieldInfo.bool()
            )))
        );
    }

    @Override
    public PotionContents parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonObject json = GSON.fromJson(fixed, JsonObject.class);
            Optional<Holder<Potion>> potion = Optional.ofNullable(
                !json.has("potion") ? null :
                    MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.POTION)
                        .get(Identifier.parse(json.get("potion").getAsString()))
                        .orElse(null)
            );
            Optional<Integer> customColor = json.has("custom_color") ? Optional.of(json.get("custom_color").getAsInt()) : Optional.empty();
            Optional<String> customName = json.has("custom_name") ? Optional.of(json.get("custom_name").getAsString()) : Optional.empty();
            List<MobEffectInstance> effects = new ArrayList<>();
            if (json.has("custom_effects")) {
                for (final JsonElement jE : json.getAsJsonArray("custom_effects")) {
                    JsonObject effectObj = jE.getAsJsonObject();
                    Holder<MobEffect> effect = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.MOB_EFFECT)
                        .get(Identifier.parse(effectObj.get("effect").getAsString()))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown effect"));
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 0;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    boolean ambient = effectObj.has("ambient") && effectObj.get("ambient").getAsBoolean();
                    boolean visible = !effectObj.has("show_particles") || effectObj.get("show_particles").getAsBoolean();
                    boolean showIcon = !effectObj.has("show_icon") || effectObj.get("show_icon").getAsBoolean();
                    effects.add(new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon));
                }
            }
            return new PotionContents(
                potion, customColor, effects, customName
            );
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<PotionContents> nms() {
        return DataComponents.POTION_CONTENTS;
    }
}
