package io.canvasmc.canvas.item.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.canvasmc.canvas.item.ComponentType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.NonNull;

public class WrittenBookContentComponent extends ComponentType<WrittenBookContent> {
    private static boolean isPageTooLarge(Component page, HolderLookup.@NonNull Provider registryAccess) {
        DataResult<JsonElement> dataResult = ComponentSerialization.CODEC.encodeStart(registryAccess.createSerializationContext(JsonOps.INSTANCE), page);
        return dataResult.isSuccess() && GsonHelper.encodesLongerThan(dataResult.getOrThrow(), 32767);
    }

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return jsonSuggestions(context, builder);
    }

    @Override
    public Map<String, FieldInfo> jsonFields() {
        return Map.of(
            "title", FieldInfo.stringField(),
            "author", FieldInfo.stringField(),
            "generation", FieldInfo.intField(),
            "pages", FieldInfo.listField(FieldInfo.stringField()),
            "resolved", FieldInfo.bool()
        );
    }

    @Override
    public WrittenBookContent parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            JsonObject asObject = json.getAsJsonObject();
            int i = 0;
            List<String> rawPages = WritableBookContentComponent.parsePages(json, i);
            List<Filterable<Component>> pages = rawPages.stream()
                .map(Component::literal)
                .filter((component) -> {
                    if (isPageTooLarge(component, MinecraftServer.getServer().registryAccess())) {
                        throw new IllegalArgumentException("Page too large for component " + component);
                    }
                    return true;
                })
                // mutable component implements Component, but we need to cast for this stream
                .map((mc) -> (Component) mc)
                .map(Filterable::passThrough)
                .toList();
            Filterable<String> title = Filterable.passThrough(asObject.get("title").getAsString());
            String author = asObject.get("author").getAsString();
            int generation = asObject.get("generation").getAsInt();
            boolean resolved = asObject.get("resolved").getAsBoolean();
            return new WrittenBookContent(title, author, generation, pages, resolved);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<WrittenBookContent> nms() {
        return DataComponents.WRITTEN_BOOK_CONTENT;
    }
}
