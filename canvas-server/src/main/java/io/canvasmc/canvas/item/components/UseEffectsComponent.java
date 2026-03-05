package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.UseEffects;

public class UseEffectsComponent extends ComponentType<UseEffects> {
    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "can_sprint", FieldInfo.bool(),
            "interact_vibrations", FieldInfo.bool(),
            "speed_multiplier", FieldInfo.floatField()
        );
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public DataComponentType<UseEffects> nms() {
        return DataComponents.USE_EFFECTS;
    }
}
