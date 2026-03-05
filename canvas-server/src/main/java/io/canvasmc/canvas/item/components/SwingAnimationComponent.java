package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;

public class SwingAnimationComponent extends ComponentType<SwingAnimation> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "type", FieldInfo.stringField(Arrays.stream(SwingAnimationType.values()).map(SwingAnimationType::getSerializedName).toArray(String[]::new)),
            "duration", FieldInfo.intField("6")
        );
    }

    @Override
    public DataComponentType<SwingAnimation> nms() {
        return DataComponents.SWING_ANIMATION;
    }
}
