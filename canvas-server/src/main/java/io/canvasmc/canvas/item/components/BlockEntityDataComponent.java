package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.NonNull;

public class BlockEntityDataComponent extends ComponentType<TypedEntityData<BlockEntityType<?>>> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet(), builder);
    }

    @Override
    public TypedEntityData<BlockEntityType<?>> parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            Identifier id = Identifier.parse(raw);
            return TypedEntityData.of(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown block entity type " + raw))
                    .value(),
                new CompoundTag()
            );
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<TypedEntityData<BlockEntityType<?>>> nms() {
        return DataComponents.BLOCK_ENTITY_DATA;
    }
}
