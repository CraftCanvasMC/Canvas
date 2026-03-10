package io.canvasmc.canvas.item.components;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.command.RootCommandTree;
import io.canvasmc.canvas.item.ComponentType;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.LockCode;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

public class LockComponent extends ComponentType<LockCode> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return ItemPredicateArgument.itemPredicate(RootCommandTree.INSTANCE.buildContext).listSuggestions(context, builder);
    }

    @Override
    public LockCode parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replace("\"", "");
            final Registry<Item> itemRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.ITEM);
            final ItemPredicate.Builder builder = ItemPredicate.Builder.item();
            if (fixed.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(fixed.substring(1)));
                builder.of(itemRegistry, tag).build();
            }
            else {
                builder.of(itemRegistry, Objects.requireNonNull(itemRegistry.getValue(Identifier.parse(fixed)), "Item not found in registry"));
            }
            return new LockCode(builder.build());
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<LockCode> nms() {
        return DataComponents.LOCK;
    }
}
