package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.NonNull;

public class EquippableComponent extends ComponentType<Equippable> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(EquipmentSlot.values()).map(EquipmentSlot::toString).map(String::toLowerCase), builder);
    }

    @Override
    public Equippable parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String full = "{\"slot\":\"" + raw + "\"}";
            JsonElement json = JsonParser.parseString(full);
            return nms().codec().parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new IllegalArgumentException("Invalid value: " + msg));
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<Equippable> nms() {
        return DataComponents.EQUIPPABLE;
    }
}
