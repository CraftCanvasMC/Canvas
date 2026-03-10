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
import java.util.Arrays;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.NonNull;

public class BannerPatternComponent extends ComponentType<BannerPatternLayers> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "layers", FieldInfo.listField(FieldInfo.objectField(
                Map.of(
                    "pattern", FieldInfo.identifierField((context) -> MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).keySet()),
                    "color", FieldInfo.stringField(Arrays.stream(DyeColor.values()).map(DyeColor::toString).map(String::toLowerCase).toList().toArray(new String[0]))
                )
            ))
        );
    }

    @Override
    public BannerPatternLayers parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonObject json = GSON.fromJson(fixed, JsonObject.class);
            List<BannerPatternLayers.Layer> layers = new ArrayList<>();
            for (final JsonElement jE : json.getAsJsonArray("layers")) {
                JsonObject layer = jE.getAsJsonObject();
                Holder<BannerPattern> pattern = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).get(
                    Identifier.parse(layer.get("pattern").getAsString())
                ).orElseThrow(() -> new IllegalArgumentException("Unknown pattern in layer " + layer));
                DyeColor color = DyeColor.valueOf(layer.get("color").getAsString().toUpperCase());
                layers.add(new BannerPatternLayers.Layer(pattern, color));
            }
            return new BannerPatternLayers(layers);
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<BannerPatternLayers> nms() {
        return DataComponents.BANNER_PATTERNS;
    }
}
