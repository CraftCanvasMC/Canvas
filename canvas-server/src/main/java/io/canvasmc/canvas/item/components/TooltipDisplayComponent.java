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
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.Map;
import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

public class TooltipDisplayComponent extends ComponentType<TooltipDisplay> {

    @Override
    public Map<String, FieldInfo> jsonFields(CommandContext<CommandSourceStack> context) {
        return Map.of(
            "hide_tooltip", FieldInfo.bool(),
            "hidden_components", FieldInfo.listField(
                FieldInfo.identifierField(ctx ->
                    ctx.getSource().getServer().registryAccess()
                        .lookupOrThrow(Registries.DATA_COMPONENT_TYPE)
                        .keySet()
                )
            )
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
    public TooltipDisplay parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            JsonObject obj = json.getAsJsonObject();

            boolean hideTooltip = obj.has("hide_tooltip") && obj.get("hide_tooltip").getAsBoolean();

            SequencedSet<DataComponentType<?>> hiddenComponents = new ReferenceLinkedOpenHashSet<>();
            if (obj.has("hidden_components")) {
                for (JsonElement e : obj.getAsJsonArray("hidden_components")) {
                    Identifier id = Identifier.parse(e.getAsString());
                    DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
                    if (type == null) throw new IllegalArgumentException("Unknown component type: " + id);
                    hiddenComponents.add(type);
                }
            }

            return new TooltipDisplay(hideTooltip, hiddenComponents);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<TooltipDisplay> nms() {
        return DataComponents.TOOLTIP_DISPLAY;
    }
}
