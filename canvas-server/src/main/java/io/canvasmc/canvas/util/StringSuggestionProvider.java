package io.canvasmc.canvas.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * Just a generally better suggestion provider...
 *
 * @author dueris
 */
public class StringSuggestionProvider implements SuggestionProvider<CommandSourceStack> {

    private final List<String> vals;

    public StringSuggestionProvider(final String... vals) {
        this.vals = Arrays.stream(vals).toList();
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(
        final CommandContext<CommandSourceStack> context,
        final SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(vals, builder);
    }
}
