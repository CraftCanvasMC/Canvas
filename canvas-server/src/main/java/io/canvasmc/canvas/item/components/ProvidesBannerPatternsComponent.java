package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jspecify.annotations.NonNull;

public class ProvidesBannerPatternsComponent extends ComponentType<TagKey<BannerPattern>> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        List<Identifier> identifiers = new ArrayList<>();
        MinecraftServer.getServer().registryAccess().lookup(Registries.BANNER_PATTERN).orElseThrow().listTagIds().forEach(id -> {
            identifiers.add(id.location());
        });
        return SharedSuggestionProvider.suggestResource(identifiers, builder);
    }

    @Override
    public TagKey<BannerPattern> parse(@NonNull final String raw) throws CommandSyntaxException {
        return TagKey.create(Registries.BANNER_PATTERN, Objects.requireNonNull(Identifier.tryParse(raw), "Identifier " + raw + " invalid"));
    }

    @Override
    public DataComponentType<TagKey<BannerPattern>> nms() {
        return DataComponents.PROVIDES_BANNER_PATTERNS;
    }
}
