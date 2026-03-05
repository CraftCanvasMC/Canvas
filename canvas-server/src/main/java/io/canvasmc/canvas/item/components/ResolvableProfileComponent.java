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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.NonNull;

public class ResolvableProfileComponent extends ComponentType<ResolvableProfile> {
    @Override
    public ResolvableProfile parse(@NonNull final String raw) throws CommandSyntaxException {
        final ServerPlayer searchedLocally = MinecraftServer.getServer().getPlayerList().getPlayerByName(raw);
        if (searchedLocally != null) {
            return ResolvableProfile.createResolved(searchedLocally.getGameProfile());
        }
        return ResolvableProfile.createUnresolved(raw.replaceAll("\"", ""));
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(MinecraftServer.getServer().getPlayerList().getPlayerNamesArray(), builder);
    }

    @Override
    public DataComponentType<ResolvableProfile> nms() {
        return DataComponents.PROFILE;
    }
}
