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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import org.jspecify.annotations.NonNull;

public class SuspiciousStewEffectsComponent extends ComponentType<SuspiciousStewEffects> {

    private static final List<String> ALL_KEYS = List.of("id", "duration");

    @Override
    public SuspiciousStewEffects parse(@NonNull final String raw) throws CommandSyntaxException {
        try {
            String fixed = raw.replaceAll(
                "([\\[,])\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)",
                "$1\"$2\""
            );
            JsonElement json = JsonParser.parseString(fixed);
            JsonArray array = json.getAsJsonArray();

            List<SuspiciousStewEffects.Entry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                Identifier effectId = Identifier.parse(obj.get("id").getAsString());
                Holder<MobEffect> effect = MinecraftServer.getServer().registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT)
                    .get(effectId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + effectId));

                int duration = obj.has("duration") ? obj.get("duration").getAsInt() : 160;

                entries.add(new SuspiciousStewEffects.Entry(effect, duration));
            }

            return new SuspiciousStewEffects(entries);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            throw new DynamicCommandExceptionType(
                obj -> Component.literal(obj.toString())
            ).create(e.getMessage());
        }
    }

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
                case "id" -> SharedSuggestionProvider.suggestResource(
                    context.getSource().getServer().registryAccess()
                        .lookupOrThrow(Registries.MOB_EFFECT)
                        .keySet().stream(),
                    offset
                );
                case "duration" -> SharedSuggestionProvider.suggest(List.of(), offset);
                default -> builder.buildFuture();
            };
        }

        Matcher midString = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)$").matcher(remaining);
        if (midString.find()) {
            String key = midString.group(1);
            int quoteStart = remaining.lastIndexOf('"');
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + quoteStart);
            if (key.equals("id")) {
                return SharedSuggestionProvider.suggestResource(
                    context.getSource().getServer().registryAccess()
                        .lookupOrThrow(Registries.MOB_EFFECT)
                        .keySet().stream(),
                    offset
                );
            }
            return builder.buildFuture();
        }

        Matcher midNumber = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9]*)$").matcher(remaining);
        if (midNumber.find() && !midNumber.group(2).isEmpty()) {
            int valueStart = remaining.lastIndexOf(':') + 1;
            while (valueStart < remaining.length() && remaining.charAt(valueStart) == ' ') valueStart++;
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + valueStart);
            return SharedSuggestionProvider.suggest(List.of(), offset);
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
            && !remaining.matches(".*\"[^\"]+\"\\s*:\\s*[0-9]+\\s*$")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of(":"), offset);
        }

        Matcher completedQuoted = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"\\s*$").matcher(remaining);
        if (completedQuoted.find()) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            List<String> next = new ArrayList<>();
            if (!usedKeys.containsAll(ALL_KEYS)) next.add(",");
            next.add("}");
            return SharedSuggestionProvider.suggest(next, offset);
        }

        Matcher completedNumeric = Pattern.compile("\"([^\"]+)\"\\s*:\\s*[0-9]+\\s*$").matcher(remaining);
        if (completedNumeric.find()) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            List<String> next = new ArrayList<>();
            if (!usedKeys.containsAll(ALL_KEYS)) next.add(",");
            next.add("}");
            return SharedSuggestionProvider.suggest(next, offset);
        }

        if (usedKeys.contains("id")) {
            SuggestionsBuilder offset = builder.createOffset(builder.getStart() + remaining.length());
            return SharedSuggestionProvider.suggest(List.of("}"), offset);
        }

        return builder.buildFuture();
    }

    @Override
    public DataComponentType<SuspiciousStewEffects> nms() {
        return DataComponents.SUSPICIOUS_STEW_EFFECTS;
    }
}
