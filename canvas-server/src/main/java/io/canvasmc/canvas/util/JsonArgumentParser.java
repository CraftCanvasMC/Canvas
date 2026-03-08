package io.canvasmc.canvas.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface JsonArgumentParser {

    default CompletableFuture<Suggestions> jsonSuggestions(
        CommandContext<CommandSourceStack> context,
        @NonNull SuggestionsBuilder builder
    ) {
        String input = builder.getRemaining();
        JsonState state = analyzeJson(input);
        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + state.suggestionStart());

        return switch (state.mode()) {
            case OPEN_BRACE -> SharedSuggestionProvider.suggest(
                List.of("{"),
                offset
            );

            case KEY -> {
                List<String> keySuggestions = state.availableKeys().stream()
                    .map(k -> "\"" + k + "\"")
                    .toList();
                List<String> all = new ArrayList<>(keySuggestions);
                if (!state.usedKeys().isEmpty()) all.add("}");
                yield SharedSuggestionProvider.suggest(all, offset);
            }

            case COLON -> SharedSuggestionProvider.suggest(List.of(":"), offset);

            case BOOL_VALUE -> SharedSuggestionProvider.suggest(List.of("true", "false"), offset);

            case INT_VALUE, FLOAT_VALUE -> {
                FieldInfo info = state.currentField();
                List<String> hints = info != null && !info.examples().isEmpty()
                    ? info.examples()
                    : List.of();
                yield SharedSuggestionProvider.suggest(hints, offset);
            }

            case STRING_VALUE -> {
                FieldInfo info = state.currentField();
                List<String> hints = info != null && !info.examples().isEmpty()
                    ? info.examples().stream().map(e -> "\"" + e + "\"").toList()
                    : List.of();
                yield SharedSuggestionProvider.suggest(hints, offset);
            }

            case LIST_VALUE -> SharedSuggestionProvider.suggest(List.of("["), offset);

            case LIST_ELEMENT -> {
                FieldInfo info = state.currentField();
                if (info == null || info.elementType() == null) yield builder.buildFuture();
                FieldInfo elemType = info.elementType();
                List<String> hints = switch (elemType.valueMode()) {
                    case BOOL_VALUE -> List.of("true", "false");
                    case INT_VALUE, FLOAT_VALUE -> elemType.examples().isEmpty() ? List.of() : elemType.examples();
                    case STRING_VALUE -> elemType.examples().stream().map(e -> "\"" + e + "\"").toList();
                    default -> List.of();
                };
                yield SharedSuggestionProvider.suggest(hints, offset);
            }

            case COMMA_OR_CLOSE -> {
                FieldInfo info = state.currentField();
                if (info != null && info.valueMode() == JsonState.Mode.LIST_VALUE) {
                    yield SharedSuggestionProvider.suggest(List.of(",", "]"), offset);
                }
                yield SharedSuggestionProvider.suggest(
                    state.availableKeys().isEmpty() ? List.of("}") : List.of(",", "}"),
                    offset
                );
            }

            case CLOSE_BRACE -> SharedSuggestionProvider.suggest(List.of("}"), offset);

            case NONE -> builder.buildFuture();
        };
    }

    default Map<String, FieldInfo> jsonFields() {
        return Map.of();
    }

    @Contract("_ -> new")
    private @NonNull JsonState analyzeJson(String input) {
        Map<String, FieldInfo> fields = jsonFields();
        Set<String> usedKeys = new HashSet<>();

        Matcher usedMatcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(input);
        while (usedMatcher.find()) {
            usedKeys.add(usedMatcher.group(1));
        }

        List<String> remainingKeys = fields.keySet().stream()
            .filter(k -> !usedKeys.contains(k))
            .toList();

        String trimmed = input.stripTrailing();

        if (trimmed.isEmpty()) {
            return new JsonState(JsonState.Mode.OPEN_BRACE, 0, remainingKeys, usedKeys, null);
        }

        if (trimmed.equals("{")) {
            return new JsonState(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys, null);
        }

        if (trimmed.endsWith("}")) {
            return new JsonState(JsonState.Mode.NONE, trimmed.length(), remainingKeys, usedKeys, null);
        }

        if (trimmed.matches(".*:\\s*(true|false|[0-9]*\\.?[0-9]+)\\s*")) {
            return new JsonState(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys, null);
        }

        if (trimmed.matches("\\{.*}\\s*")) {
            return new JsonState(JsonState.Mode.NONE, trimmed.length(), remainingKeys, usedKeys, null);
        }

        if (trimmed.matches(".*:\\s*\"[^\"]*\"\\s*")) {
            return new JsonState(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys, null);
        }

        if (trimmed.endsWith(",")) {
            return new JsonState(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys, null);
        }

        Matcher insideList = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^]]*)$").matcher(trimmed);
        if (insideList.find()) {
            String key = insideList.group(1);
            String listContent = insideList.group(2);
            FieldInfo info = fields.get(key);
            if (info != null && info.valueMode() == JsonState.Mode.LIST_VALUE) {
                if (listContent.stripTrailing().endsWith("[") || listContent.stripTrailing().endsWith(",")) {
                    return new JsonState(JsonState.Mode.LIST_ELEMENT, trimmed.length(), remainingKeys, usedKeys, info);
                }
                if (listContent.matches(".*([^\",\\[])\\s*") || listContent.matches(".*\"[^\"]+\"\\s*")) {
                    return new JsonState(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys, info);
                }
                return new JsonState(JsonState.Mode.LIST_ELEMENT, trimmed.length(), remainingKeys, usedKeys, info);
            }
        }

        Matcher closedList = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[[^]]*]\\s*$").matcher(trimmed);
        if (closedList.find()) {
            return new JsonState(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys, null);
        }

        Matcher afterColon = Pattern.compile("\"([^\"]+)\"\\s*:\\s*$").matcher(trimmed);
        if (afterColon.find()) {
            String key = afterColon.group(1);
            FieldInfo info = fields.get(key);
            if (info != null) {
                return new JsonState(info.valueMode(), trimmed.length(), remainingKeys, usedKeys, info);
            }
        }

        Matcher midBool = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(t|tr|tru|f|fa|fal|fals)$").matcher(trimmed);
        if (midBool.find()) {
            String key = midBool.group(1);
            FieldInfo info = fields.get(key);
            if (info != null && info.valueMode() == JsonState.Mode.BOOL_VALUE) {
                int valueStart = trimmed.lastIndexOf(':') + 1;
                while (valueStart < trimmed.length() && trimmed.charAt(valueStart) == ' ') valueStart++;
                return new JsonState(JsonState.Mode.BOOL_VALUE, valueStart, remainingKeys, usedKeys, info);
            }
        }

        Matcher midNumber = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([0-9]*\\.?[0-9]*)$").matcher(trimmed);
        if (midNumber.find() && !midNumber.group(2).isEmpty()) {
            String key = midNumber.group(1);
            FieldInfo info = fields.get(key);
            if (info != null && (info.valueMode() == JsonState.Mode.FLOAT_VALUE || info.valueMode() == JsonState.Mode.INT_VALUE)) {
                int valueStart = trimmed.lastIndexOf(':') + 1;
                while (valueStart < trimmed.length() && trimmed.charAt(valueStart) == ' ') valueStart++;
                return new JsonState(info.valueMode(), valueStart, remainingKeys, usedKeys, info);
            }
        }

        if (trimmed.matches(".*\"[^\"]+\"\\s*$") && lastKeyHasNoColon(trimmed)) {
            return new JsonState(JsonState.Mode.COLON, trimmed.length(), remainingKeys, usedKeys, null);
        }

        Matcher midKey = Pattern.compile("(?:\\{|,)\\s*\"([^\"]*)$").matcher(trimmed);
        if (midKey.find()) {
            int quoteStart = trimmed.lastIndexOf('"');
            return new JsonState(JsonState.Mode.KEY, quoteStart, remainingKeys, usedKeys, null);
        }

        Matcher afterSep = Pattern.compile("(?:\\{|,)\\s*$").matcher(trimmed);
        if (afterSep.find()) {
            return new JsonState(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys, null);
        }

        return new JsonState(JsonState.Mode.NONE, input.length(), remainingKeys, usedKeys, null);
    }

    private boolean lastKeyHasNoColon(@NonNull String input) {
        int lastClose = input.lastIndexOf('"');
        if (lastClose < 0) return false;
        int lastOpen = input.lastIndexOf('"', lastClose - 1);
        if (lastOpen < 0) return false;
        String after = input.substring(lastClose + 1).trim();
        return !after.startsWith(":");
    }

    record JsonState(
        Mode mode,
        int suggestionStart,
        List<String> availableKeys,
        Set<String> usedKeys,
        @org.jspecify.annotations.Nullable FieldInfo currentField
    ) {
        public enum Mode {
            OPEN_BRACE,
            KEY,
            COLON,
            BOOL_VALUE,
            INT_VALUE,
            FLOAT_VALUE,
            STRING_VALUE,
            LIST_VALUE,
            LIST_ELEMENT,
            COMMA_OR_CLOSE,
            CLOSE_BRACE,
            NONE
        }
    }

    record FieldInfo(JsonState.Mode valueMode, List<String> examples, @Nullable FieldInfo elementType) {
        @Contract(" -> new")
        public static @NonNull FieldInfo bool() {
            return new FieldInfo(JsonState.Mode.BOOL_VALUE, List.of(), null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo floatField(String... examples) {
            return new FieldInfo(JsonState.Mode.FLOAT_VALUE, List.of(examples), null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo intField(String... examples) {
            return new FieldInfo(JsonState.Mode.INT_VALUE, List.of(examples), null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo stringField(String... examples) {
            return new FieldInfo(JsonState.Mode.STRING_VALUE, List.of(examples), null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo listField(@NonNull FieldInfo elementType) {
            return new FieldInfo(JsonState.Mode.LIST_VALUE, List.of(), elementType);
        }
    }
}
