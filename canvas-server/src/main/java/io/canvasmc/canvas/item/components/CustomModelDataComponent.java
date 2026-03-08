package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CustomModelDataComponent extends ComponentType<CustomModelData> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "floats", FieldInfo.listField(FieldInfo.floatField()),
            "flags", FieldInfo.listField(FieldInfo.bool()),
            "strings", FieldInfo.listField(FieldInfo.stringField()),
            "colors", FieldInfo.listField(FieldInfo.intField())
        );
    }

    @Override
    public DataComponentType<CustomModelData> nms() {
        return DataComponents.CUSTOM_MODEL_DATA;
    }
}
