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
import net.minecraft.world.item.component.AttackRange;

public class AttackRangeComponent extends ComponentType<AttackRange> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "min_reach", FieldInfo.floatField("0.0"),
            "max_reach", FieldInfo.floatField("3.0"),
            "min_creative_reach", FieldInfo.floatField("0.0"),
            "max_creative_reach", FieldInfo.floatField("5.0"),
            "hitbox_margin", FieldInfo.floatField("0.3"),
            "mob_factor", FieldInfo.floatField("1.0")
        );
    }

    @Override
    public DataComponentType<AttackRange> nms() {
        return DataComponents.ATTACK_RANGE;
    }
}
