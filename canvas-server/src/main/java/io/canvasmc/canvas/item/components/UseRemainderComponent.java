package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.UseRemainder;
import org.jspecify.annotations.NonNull;

public class UseRemainderComponent extends ComponentType<UseRemainder> {
    @Override
    public UseRemainder parse(@NonNull final String raw) throws CommandSyntaxException {
        return new UseRemainder(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(raw.replaceAll("\"", ""))).getDefaultInstance());
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
    }

    @Override
    public DataComponentType<UseRemainder> nms() {
        return DataComponents.USE_REMAINDER;
    }
}
