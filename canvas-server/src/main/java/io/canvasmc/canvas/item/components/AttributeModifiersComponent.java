package io.canvasmc.canvas.item.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.canvasmc.canvas.item.ComponentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.NonNull;

public class AttributeModifiersComponent extends ComponentType<ItemAttributeModifiers> {

    private static final List<String> ALL_KEYS = List.of("attribute", "amount", "operation", "slot_group");

    @Override
    public CompletableFuture<Suggestions> suggestions(final CommandContext<CommandSourceStack> context, final @NonNull SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().stripTrailing();

        if (remaining.isEmpty()) {
            return SharedSuggestionProvider.suggest(List.of("["), builder);
        }
        if (remaining.endsWith("]")) return builder.buildFuture();

        if (remaining.matches(".*}\\s*")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of(",", "]"), offset);
        }

        if (remaining.endsWith("[") || remaining.matches(".*},\\s*")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of("{"), offset);
        }

        int entryStart = Math.max(remaining.lastIndexOf('['), remaining.lastIndexOf('{'));
        String currentEntry = remaining.substring(entryStart);

        if (!currentEntry.contains("{")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of("{"), offset);
        }

        Set<String> usedKeys = new HashSet<>();
        Matcher usedMatcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(currentEntry);
        while (usedMatcher.find()) usedKeys.add(usedMatcher.group(1));

        if (currentEntry.endsWith("{") || currentEntry.endsWith(",")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(
                ALL_KEYS.stream().filter(k -> !usedKeys.contains(k)).map(k -> "\"" + k + "\""),
                offset
            );
        }

        Matcher afterColon = Pattern.compile("\"([^\"]+)\"\\s*:\\s*$").matcher(remaining);
        if (afterColon.find()) {
            String key = afterColon.group(1);
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return switch (key) {
                case "attribute" -> SharedSuggestionProvider.suggestResource(
                    BuiltInRegistries.ATTRIBUTE.keySet().stream(), offset
                );
                case "operation" -> SharedSuggestionProvider.suggest(
                    Arrays.stream(AttributeModifier.Operation.values()).map(o -> "\"" + o.name().toLowerCase() + "\""),
                    offset
                );
                case "slot_group" -> SharedSuggestionProvider.suggest(
                    Arrays.stream(EquipmentSlotGroup.values()).map(s -> "\"" + s.getSerializedName() + "\""),
                    offset
                );
                case "amount" -> SharedSuggestionProvider.suggest(List.of("1.0"), offset);
                default -> builder.buildFuture();
            };
        }

        Matcher completedQuotedValue = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"\\s*$").matcher(remaining);
        if (completedQuotedValue.find()) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            List<String> next = new ArrayList<>();
            if (!usedKeys.containsAll(ALL_KEYS)) next.add(",");
            next.add("}");
            return SharedSuggestionProvider.suggest(next, offset);
        }

        Matcher completedNumericValue = Pattern.compile("\"([^\"]+)\"\\s*:\\s*[0-9.]+\\s*$").matcher(remaining);
        if (completedNumericValue.find()) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            List<String> next = new ArrayList<>();
            if (!usedKeys.containsAll(ALL_KEYS)) next.add(",");
            next.add("}");
            return SharedSuggestionProvider.suggest(next, offset);
        }

        Matcher midString = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)$").matcher(remaining);
        if (midString.find()) {
            String key = midString.group(1);
            int quoteStart = remaining.lastIndexOf('"');
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + quoteStart);
            return switch (key) {
                case "attribute" -> SharedSuggestionProvider.suggest(
                    BuiltInRegistries.ATTRIBUTE.keySet().stream().map(id -> "\"" + id + "\""),
                    offset
                );
                case "operation" -> SharedSuggestionProvider.suggest(
                    Arrays.stream(AttributeModifier.Operation.values()).map(o -> "\"" + o.name().toLowerCase() + "\""),
                    offset
                );
                case "slot_group" -> SharedSuggestionProvider.suggest(
                    Arrays.stream(EquipmentSlotGroup.values()).map(s -> "\"" + s.getSerializedName() + "\""),
                    offset
                );
                default -> builder.buildFuture();
            };
        }

        Matcher midKey = Pattern.compile("(?:\\{|,)\\s*\"([^\"]*)$").matcher(remaining);
        if (midKey.find()) {
            int quoteStart = remaining.lastIndexOf('"');
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + quoteStart);
            return SharedSuggestionProvider.suggest(
                ALL_KEYS.stream().filter(k -> !usedKeys.contains(k)).map(k -> "\"" + k + "\""),
                offset
            );
        }

        if (remaining.matches(".*\"[^\"]+\"\\s*$")
            && !remaining.matches(".*\"[^\"]+\"\\s*:\\s*\"[^\"]+\"\\s*$")
            && !remaining.matches(".*\"[^\"]+\"\\s*:\\s*[0-9.]+\\s*$")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of(":"), offset);
        }

        if (usedKeys.containsAll(ALL_KEYS)) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of("}"), offset);
        }

        return builder.buildFuture();
    }

    @Override
    public ItemAttributeModifiers parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,{:])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)(?=[\\s,}\\]])",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            JsonArray array = json.getAsJsonArray();

            List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
            int i = 0;
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                Identifier attributeId = Identifier.parse(obj.get("attribute").getAsString());
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                    .get(attributeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + attributeId));

                double amount = obj.get("amount").getAsDouble();

                AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                    obj.get("operation").getAsString().toUpperCase()
                );

                EquipmentSlotGroup slotGroup = EquipmentSlotGroup.valueOf(obj.get("slot_group").getAsString().toUpperCase());

                Identifier modifierId = Identifier.parse("canvas:modifier_" + attributeId.getPath() + "_" + amount + "_" + i);

                entries.add(new ItemAttributeModifiers.Entry(
                    attribute,
                    new AttributeModifier(modifierId, amount, operation),
                    slotGroup
                ));
                i++;
            }

            return new ItemAttributeModifiers(entries);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

    @Override
    public DataComponentType<ItemAttributeModifiers> nms() {
        return DataComponents.ATTRIBUTE_MODIFIERS;
    }
}
