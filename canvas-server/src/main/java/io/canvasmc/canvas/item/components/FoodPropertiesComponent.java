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
import net.minecraft.world.food.FoodProperties;

public class FoodPropertiesComponent extends ComponentType<FoodProperties> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "nutrition", FieldInfo.intField(),
            "saturation", FieldInfo.floatField(),
            "can_always_eat", FieldInfo.bool()
        );
    }

    @Override
    public DataComponentType<FoodProperties> nms() {
        return DataComponents.FOOD;
    }
}
