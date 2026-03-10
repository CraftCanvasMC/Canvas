package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.command.RootCommandTree;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.jspecify.annotations.NonNull;

public class ChargedProjectilesComponent extends ComponentType<ChargedProjectiles> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return listOfSuggestions(context, builder, (newContext, newBuilder) -> ItemArgument.item(RootCommandTree.INSTANCE.buildContext).listSuggestions(newContext, newBuilder));
    }

    @Override
    public ChargedProjectiles parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = GSON.fromJson(fixed, JsonElement.class);

            Map<Identifier, ItemStack> stacks = new LinkedHashMap<>();

            for (JsonElement element : json.getAsJsonArray()) {
                Identifier id = Identifier.tryParse(element.getAsString());
                if (id == null) {
                    throw new IllegalArgumentException("Invalid item id: " + element.getAsString());
                }

                ItemStack stack = stacks.get(id);

                if (stack != null) {
                    stack.setCount(stack.getCount() + 1);
                    continue;
                }

                stack = new ItemStack(
                    MinecraftServer.getServer()
                        .registryAccess()
                        .lookupOrThrow(Registries.ITEM)
                        .get(id)
                        .orElseThrow(),
                    1, DataComponentPatch.builder().set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE).build()
                );

                stacks.put(id, stack);
            }

            return ChargedProjectiles.of(new ArrayList<>(stacks.values()));
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<ChargedProjectiles> nms() {
        return DataComponents.CHARGED_PROJECTILES;
    }
}
