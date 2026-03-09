package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WritableBookContent;
import org.jspecify.annotations.NonNull;

public class WritableBookContentComponent extends ComponentType<WritableBookContent> {
    static @NonNull List<String> parsePages(final @NonNull JsonElement json, int i) throws CommandSyntaxException {
        List<String> pages = new ArrayList<>();
        for (final JsonElement jE : json.getAsJsonObject().get("pages").getAsJsonArray()) {
            String page = jE.getAsString();
            if (page.length() > 266) {
                throw new DynamicCommandExceptionType(
                    obj -> Component.literal("Page at i(" + obj + ") is too long! Must be <= 266 characters")
                ).create(i);
            }
            pages.add(page);
            i++;
        }
        return pages;
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "pages", FieldInfo.listField(FieldInfo.stringField())
        );
    }

    @Override
    public WritableBookContent parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            int i = 0;
            List<String> pages = parsePages(json, i);
            return new WritableBookContent(pages.stream().map(Filterable::passThrough).toList());
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<WritableBookContent> nms() {
        return DataComponents.WRITABLE_BOOK_CONTENT;
    }
}
