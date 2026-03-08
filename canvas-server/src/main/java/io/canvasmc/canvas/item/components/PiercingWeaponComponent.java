package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.PiercingWeapon;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PiercingWeaponComponent extends ComponentType<PiercingWeapon> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "deals_knockback", FieldInfo.bool(),
            "dismounts", FieldInfo.bool()
        );
    }

    @Override
    public DataComponentType<PiercingWeapon> nms() {
        return DataComponents.PIERCING_WEAPON;
    }
}
