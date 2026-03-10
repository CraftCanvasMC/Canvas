package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.jspecify.annotations.NonNull;

public class BundleContentsComponent extends ComponentType<BundleContents> {
    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "contents", FieldInfo.listField(FieldInfo.objectField(
                Map.of(
                    "count", FieldInfo.intField(),
                    "item", FieldInfo.identifierField((context) -> BuiltInRegistries.ITEM.keySet())
                )
            ))
        );
    }

    @Override
    public BundleContents parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonObject json = GSON.fromJson(fixed, JsonObject.class);
            BundleContents.Mutable mutable = new BundleContents.Mutable(new BundleContents(new ArrayList<>()));
            for (final JsonElement jE : json.getAsJsonArray("contents")) {
                JsonObject jsonStack = jE.getAsJsonObject();
                int count = jsonStack.has("count") ? jsonStack.get("count").getAsInt() : 1;
                Holder<Item> item = BuiltInRegistries.ITEM.get(Identifier.parse(jsonStack.get("item").getAsString())).orElseThrow();
                ItemStack stack = new ItemStack(item, count);
                mutable.tryInsert(stack);
            }
            return mutable.toImmutable();
        } catch (Throwable thrown) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(thrown.getMessage());
        }
    }

    @Override
    public DataComponentType<BundleContents> nms() {
        return DataComponents.BUNDLE_CONTENTS;
    }
}
