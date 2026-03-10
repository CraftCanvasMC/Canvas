package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.component.DamageResistant;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DamageResistantComponent extends ComponentType<DamageResistant> {
    @Override
    public DamageResistant parse(@NonNull final String raw) throws CommandSyntaxException {
        TagKey<DamageType> tagKey = TagKey.create(Registries.DAMAGE_TYPE, Objects.requireNonNull(Identifier.tryParse(raw), "Identifier " + raw + " invalid"));
        return new DamageResistant(tagKey);
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        List<Identifier> identifiers = new ArrayList<>();
        MinecraftServer.getServer().registryAccess().lookup(Registries.DAMAGE_TYPE).orElseThrow().listTagIds().forEach(id -> {
            identifiers.add(id.location());
        });
        return SharedSuggestionProvider.suggestResource(identifiers, builder);
    }

    @Override
    public DataComponentType<DamageResistant> nms() {
        return DataComponents.DAMAGE_RESISTANT;
    }
}
