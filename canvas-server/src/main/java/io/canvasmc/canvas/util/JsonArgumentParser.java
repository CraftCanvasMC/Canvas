package io.canvasmc.canvas.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface JsonArgumentParser {

    private static @NonNull JsonState analyzeObjectListArray(
        String trimmed,
        @NonNull String arrayContent,
        FieldInfo info,
        List<String> remainingKeys,
        Set<String> usedKeys
    ) {
        int lastClose = arrayContent.lastIndexOf(']');
        if (lastClose >= 0 && !arrayContent.substring(lastClose + 1).contains("{"))
            return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);

        String arrTrimmed = arrayContent.stripTrailing();

        if (arrTrimmed.isEmpty() || endsWithArrayLevelComma(arrTrimmed))
            return new JsonState(JsonState.Mode.LIST_OBJECT_OPEN, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);

        if (arrTrimmed.endsWith("}"))
            return new JsonState(JsonState.Mode.ARRAY_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);

        int lastOpen = lastUnclosedBrace(arrayContent);
        if (lastOpen < 0)
            return new JsonState(JsonState.Mode.LIST_OBJECT_OPEN, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);

        return analyzeEntryObject(trimmed, arrayContent.substring(lastOpen), info, remainingKeys, usedKeys);
    }

    private static @NonNull JsonState analyzeEntryObject(
        String trimmed,
        String currentObj,
        @NonNull FieldInfo info,
        List<String> remainingKeys,
        Set<String> usedKeys
    ) {
        assert info.entryFields() != null;
        Map<String, FieldInfo> entryFields = info.entryFields();

        Set<String> entryUsedKeys = new HashSet<>();
        Matcher entryKeyMatcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(currentObj);
        while (entryKeyMatcher.find()) entryUsedKeys.add(entryKeyMatcher.group(1));

        if (currentObj.endsWith("{") || currentObj.endsWith(","))
            return new JsonState(JsonState.Mode.LIST_OBJECT_KEY, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        Matcher afterColon = Pattern.compile("\"([^\"]+)\"\\s*:\\s*$").matcher(currentObj);
        if (afterColon.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_VALUE, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, afterColon.group(1), info);

        Matcher completedQuoted = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"[^\"]+\"\\s*$").matcher(currentObj);
        if (completedQuoted.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        Matcher completedUnquotedPrimitive = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(true|false|-?[0-9]*\\.?[0-9]+)\\s+$"
        ).matcher(currentObj);
        if (completedUnquotedPrimitive.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        Matcher completedIdentifier = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*[a-z0-9_.-]+:[a-z0-9_./-]+\\s*$"
        ).matcher(currentObj);
        if (completedIdentifier.find()) {
            String entryKey = completedIdentifier.group(1);
            if (entryFields.containsKey(entryKey))
                return new JsonState(JsonState.Mode.LIST_OBJECT_COMMA_OR_CLOSE, trimmed.length(),
                    remainingKeys, usedKeys, entryUsedKeys, null, info);
        }

        Matcher midQuoted = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)$").matcher(currentObj);
        if (midQuoted.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_VALUE, trimmed.lastIndexOf('"'),
                remainingKeys, usedKeys, entryUsedKeys, midQuoted.group(1), info);

        Matcher midIdentifier = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*([a-z0-9_.-]*:?[a-z0-9_./-]*)$"
        ).matcher(currentObj);
        if (midIdentifier.find() && !midIdentifier.group(2).isEmpty())
            return new JsonState(JsonState.Mode.LIST_OBJECT_VALUE,
                trimmed.length() - midIdentifier.group(2).length(),
                remainingKeys, usedKeys, entryUsedKeys, midIdentifier.group(1), info);

        Matcher midNumber = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?[0-9]*\\.?[0-9]+)$").matcher(currentObj);
        if (midNumber.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_VALUE,
                trimmed.length() - midNumber.group(2).length(),
                remainingKeys, usedKeys, entryUsedKeys, midNumber.group(1), info);

        Matcher midBool = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(t|tr|tru|f|fa|fal|fals)$"
        ).matcher(currentObj);
        if (midBool.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_VALUE,
                trimmed.length() - midBool.group(2).length(),
                remainingKeys, usedKeys, entryUsedKeys, midBool.group(1), info);

        Matcher midKey = Pattern.compile("(?:\\{|,)\\s*\"([^\"]*)$").matcher(currentObj);
        if (midKey.find())
            return new JsonState(JsonState.Mode.LIST_OBJECT_KEY, trimmed.lastIndexOf('"'),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        if (currentObj.matches(".*\"[^\"]+\"\\s*$") && lastKeyHasNoColon(currentObj))
            return new JsonState(JsonState.Mode.COLON, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        if (!entryUsedKeys.isEmpty() && entryUsedKeys.containsAll(entryFields.keySet()))
            return new JsonState(JsonState.Mode.LIST_OBJECT_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, entryUsedKeys, null, info);

        return bare(JsonState.Mode.NONE, trimmed.length(), remainingKeys, usedKeys);
    }

    @Contract("_, _, _, _, _ -> new")
    private static @NonNull JsonState analyzeSimpleListArray(
        String trimmed,
        @NonNull String listContent,
        FieldInfo info,
        List<String> remainingKeys,
        Set<String> usedKeys
    ) {
        String trimmedList = listContent.stripTrailing();
        if (trimmedList.isEmpty() || trimmedList.endsWith(","))
            return new JsonState(JsonState.Mode.LIST_ELEMENT, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);
        if (trimmedList.matches(".*\"[^\"]*$"))
            return new JsonState(JsonState.Mode.LIST_ELEMENT, trimmed.lastIndexOf('"'),
                remainingKeys, usedKeys, Set.of(), null, info);
        if (trimmedList.matches(".*\"[^\"]+\"\\s*$"))
            return new JsonState(JsonState.Mode.LIST_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);
        Matcher midUnquoted = Pattern.compile("(?:^|[,\\[])\\s*([a-z0-9_.-]*:?[a-z0-9_./-]*)$").matcher(trimmedList);
        if (midUnquoted.find() && !midUnquoted.group(1).isEmpty())
            return new JsonState(JsonState.Mode.LIST_ELEMENT,
                trimmed.length() - midUnquoted.group(1).length(),
                remainingKeys, usedKeys, Set.of(), null, info);
        if (trimmedList.matches(".*[a-z0-9_./:+-]\\s+"))
            return new JsonState(JsonState.Mode.LIST_COMMA_OR_CLOSE, trimmed.length(),
                remainingKeys, usedKeys, Set.of(), null, info);

        return new JsonState(JsonState.Mode.LIST_ELEMENT, trimmed.length(),
            remainingKeys, usedKeys, Set.of(), null, info);
    }

    private static boolean isFullyClosed(@NonNull String trimmed) {
        if (trimmed.endsWith("}")) {
            int depth = 0;
            for (char c : trimmed.toCharArray()) {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            return depth == 0;
        }
        return false;
    }

    private static boolean topLevelEndsWithComma(@NonNull String trimmed) {
        if (!trimmed.endsWith(",")) return false;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
        }
        return depth == 1;
    }

    private static boolean topLevelCompletedPrimitive(@NonNull String trimmed) {
        return trimmed.matches(".*:\\s*(true|false|[0-9]*\\.?[0-9]+)\\s*$")
            && !isInsideNestedValue(trimmed);
    }

    private static boolean topLevelCompletedQuotedString(@NonNull String trimmed) {
        return trimmed.matches(".*:\\s*\"[^\"]+\"\\s*$")
            && !isInsideNestedValue(trimmed);
    }

    private static boolean isInsideNestedValue(@NonNull String trimmed) {
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon < 0) return false;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i <= lastColon; i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
        }
        return depth > 1;
    }

    private static boolean lastKeyHasNoColon(@NonNull String input) {
        int lastClose = input.lastIndexOf('"');
        if (lastClose < 0) return false;
        int lastOpen = input.lastIndexOf('"', lastClose - 1);
        if (lastOpen < 0) return false;
        return !input.substring(lastClose + 1).trim().startsWith(":");
    }

    private static int valueTokenStart(@NonNull String trimmed) {
        int colonPos = trimmed.lastIndexOf(':');
        if (colonPos < 0) return trimmed.length();
        int i = colonPos + 1;
        while (i < trimmed.length() && trimmed.charAt(i) == ' ') i++;
        return i;
    }

    private static int lastUnclosedBrace(@NonNull String arrayContent) {
        int depth = 0;
        int lastOpen = -1;
        boolean inString = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '"' && (i == 0 || arrayContent.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{') {
                depth++;
                lastOpen = i;
            }
            else if (c == '}') {
                depth--;
            }
        }
        return depth > 0 ? lastOpen : -1;
    }

    private static boolean endsWithArrayLevelComma(@NonNull String arrayContent) {
        int depth = 0;
        int lastTopComma = -1;
        boolean inString = false;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '"' && (i == 0 || arrayContent.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) lastTopComma = i;
        }
        if (lastTopComma < 0) return false;
        return arrayContent.substring(lastTopComma + 1).isBlank();
    }

    @Contract("_, _, _, _ -> new")
    private static @NonNull JsonState bare(
        JsonState.Mode mode, int start,
        List<String> remaining, Set<String> used
    ) {
        return new JsonState(mode, start, remaining, used, Set.of(), null, null);
    }

    private static int indexOfSplitter(@NonNull String str, int fromIndex) {
        for (int i = fromIndex; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == ':' || c == '_' || c == '.' || c == '-' || c == '/') return i;
        }
        return -1;
    }

    private static @NonNull String commonPrefix(@NonNull String a, @NonNull String b) {
        int len = Math.min(a.length(), b.length());
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) return a.substring(0, i);
        }
        return a.substring(0, len);
    }

    static CompletableFuture<Suggestions> suggestIdentifiers(
        @NonNull Iterable<Identifier> resources,
        @NonNull SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        boolean hasColon = remaining.indexOf(':') > -1;
        for (Identifier id : resources) {
            if (remaining.isEmpty()
                || (hasColon
                ? matchesSubStr(remaining, id.toString())
                : matchesSubStr(remaining, id.getNamespace()) || matchesSubStr(remaining, id.getPath()))) {
                builder.suggest(id.toString());
            }
        }
        return builder.buildFuture();
    }

    @Deprecated
    static CompletableFuture<Suggestions> suggestIdentifiers(
        Iterable<Identifier> resources,
        SuggestionsBuilder builder,
        @SuppressWarnings("unused") String ignoredPrefix
    ) {
        return suggestIdentifiers(resources, builder);
    }

    static <T> void filterResources(
        Iterable<T> resources,
        @NonNull String remaining,
        String prefix,
        Function<T, Identifier> locationFunction,
        Consumer<T> resourceConsumer
    ) {
        if (remaining.isEmpty()) {
            resources.forEach(resourceConsumer);
        }
        else {
            String common = commonPrefix(remaining, prefix);
            String sub = common.isEmpty() ? remaining : remaining.substring(common.length());
            filterResources(resources, sub, locationFunction, resourceConsumer);
        }
    }

    static <T> void filterResources(
        @NonNull Iterable<T> resources,
        @NonNull String input,
        Function<T, Identifier> locationFunction,
        Consumer<T> resourceConsumer
    ) {
        boolean hasColon = input.indexOf(':') > -1;
        for (T object : resources) {
            Identifier id = locationFunction.apply(object);
            if (hasColon) {
                if (matchesSubStr(input, id.toString())) resourceConsumer.accept(object);
            }
            else if (matchesSubStr(input, id.getNamespace())
                || matchesSubStr(input, id.getPath())) {
                resourceConsumer.accept(object);
            }
        }
    }

    static boolean matchesSubStr(String input, @NonNull String substring) {
        int i = 0;
        while (!substring.startsWith(input, i)) {
            int next = indexOfSplitter(substring, i);
            if (next < 0) return false;
            i = next + 1;
        }
        return true;
    }

    default CompletableFuture<Suggestions> jsonSuggestions(
        CommandContext<CommandSourceStack> context,
        @NonNull SuggestionsBuilder builder
    ) {
        String input = builder.getRemaining();
        JsonState state = analyzeJson(input, context);
        SuggestionsBuilder offset = builder.createOffset(builder.getStart() + state.suggestionStart());

        return switch (state.mode()) {

            case OPEN_BRACE -> SharedSuggestionProvider.suggest(List.of("{"), offset);

            case KEY -> {
                List<String> keys = state.availableKeys().stream()
                    .map(k -> "\"" + k + "\"")
                    .toList();
                List<String> all = new ArrayList<>(keys);
                if (!state.usedKeys().isEmpty()) all.add("}");
                yield SharedSuggestionProvider.suggest(all, offset);
            }

            case COLON -> SharedSuggestionProvider.suggest(List.of(":"), offset);

            case BOOL_VALUE -> SharedSuggestionProvider.suggest(List.of("true", "false"), offset);

            case INT_VALUE, FLOAT_VALUE -> {
                FieldInfo info = state.currentField();
                yield SharedSuggestionProvider.suggest(
                    info != null ? info.resolveExamples(context) : List.of(), offset
                );
            }

            case STRING_VALUE -> {
                FieldInfo info = state.currentField();
                List<String> examples = info != null ? info.resolveExamples(context) : List.of();
                yield SharedSuggestionProvider.suggest(
                    examples.stream()
                        .map(e -> e.startsWith("\"") ? e : "\"" + e + "\"")
                        .toList(),
                    offset
                );
            }

            case IDENTIFIER_VALUE -> {
                FieldInfo info = state.currentField();
                if (info == null) yield builder.buildFuture();
                Iterable<Identifier> ids = info.identifierSupplier() != null
                    ? info.identifierSupplier().apply(context)
                    : List.of();
                yield suggestIdentifiers(ids, offset);
            }

            case LIST_VALUE -> SharedSuggestionProvider.suggest(List.of("["), offset);

            case LIST_ELEMENT -> {
                FieldInfo info = state.currentField();
                if (info == null || info.elementType() == null) yield builder.buildFuture();
                FieldInfo elem = info.elementType();
                yield switch (elem.valueMode()) {
                    case BOOL_VALUE -> SharedSuggestionProvider.suggest(List.of("true", "false"), offset);
                    case INT_VALUE, FLOAT_VALUE ->
                        SharedSuggestionProvider.suggest(elem.resolveExamples(context), offset);
                    case STRING_VALUE -> SharedSuggestionProvider.suggest(
                        elem.resolveExamples(context).stream()
                            .map(e -> e.startsWith("\"") ? e : "\"" + e + "\"")
                            .toList(),
                        offset
                    );
                    case IDENTIFIER_VALUE -> {
                        Iterable<Identifier> ids = elem.identifierSupplier() != null
                            ? elem.identifierSupplier().apply(context)
                            : List.of();
                        yield suggestIdentifiers(ids, offset);
                    }
                    default -> builder.buildFuture();
                };
            }

            case LIST_COMMA_OR_CLOSE -> SharedSuggestionProvider.suggest(List.of(",", "]"), offset);

            case LIST_OBJECT -> SharedSuggestionProvider.suggest(List.of("["), offset);

            case LIST_OBJECT_OPEN -> SharedSuggestionProvider.suggest(List.of("{"), offset);

            case LIST_OBJECT_KEY -> {
                FieldInfo info = state.currentField();
                if (info == null || info.entryFields() == null) yield builder.buildFuture();
                List<String> keys = info.entryFields().keySet().stream()
                    .filter(k -> !state.usedEntryKeys().contains(k))
                    .map(k -> "\"" + k + "\"")
                    .toList();
                List<String> all = new ArrayList<>(keys);
                if (!state.usedEntryKeys().isEmpty()) all.add("}");
                yield SharedSuggestionProvider.suggest(all, offset);
            }

            case LIST_OBJECT_VALUE -> {
                FieldInfo info = state.currentField();
                if (info == null || info.entryFields() == null) yield builder.buildFuture();
                String entryKey = state.currentEntryKey();
                if (entryKey == null) yield builder.buildFuture();
                FieldInfo valueInfo = info.entryFields().get(entryKey);
                if (valueInfo == null) yield builder.buildFuture();
                yield switch (valueInfo.valueMode()) {
                    case BOOL_VALUE -> SharedSuggestionProvider.suggest(List.of("true", "false"), offset);
                    case INT_VALUE, FLOAT_VALUE ->
                        SharedSuggestionProvider.suggest(valueInfo.resolveExamples(context), offset);
                    case STRING_VALUE -> SharedSuggestionProvider.suggest(
                        valueInfo.resolveExamples(context).stream()
                            .map(e -> e.startsWith("\"") ? e : "\"" + e + "\"")
                            .toList(),
                        offset
                    );
                    case IDENTIFIER_VALUE -> {
                        Iterable<Identifier> ids = valueInfo.identifierSupplier() != null
                            ? valueInfo.identifierSupplier().apply(context)
                            : List.of();
                        yield suggestIdentifiers(ids, offset);
                    }
                    default -> builder.buildFuture();
                };
            }

            case LIST_OBJECT_COMMA_OR_CLOSE -> {
                FieldInfo info = state.currentField();
                if (info == null || info.entryFields() == null) yield builder.buildFuture();
                List<String> next = new ArrayList<>();
                if (!state.usedEntryKeys().containsAll(info.entryFields().keySet()))
                    next.add(",");
                next.add("}");
                yield SharedSuggestionProvider.suggest(next, offset);
            }

            case ARRAY_COMMA_OR_CLOSE -> SharedSuggestionProvider.suggest(List.of(",", "]"), offset);

            case COMMA_OR_CLOSE -> SharedSuggestionProvider.suggest(
                state.availableKeys().isEmpty() ? List.of("}") : List.of(",", "}"),
                offset
            );

            case CLOSE_BRACE -> SharedSuggestionProvider.suggest(List.of("}"), offset);
            case NONE -> builder.buildFuture();
        };
    }

    default Map<String, FieldInfo> jsonFields() {
        return Map.of();
    }

    default Map<String, FieldInfo> jsonFields(CommandContext<CommandSourceStack> context) {
        return jsonFields();
    }

    @Contract("_, _ -> new")
    private @NonNull JsonState analyzeJson(String input, CommandContext<CommandSourceStack> context) {
        Map<String, FieldInfo> fields = jsonFields(context);

        Set<String> usedKeys = new HashSet<>();
        Matcher usedMatcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(input);
        while (usedMatcher.find()) usedKeys.add(usedMatcher.group(1));

        List<String> remainingKeys = fields.keySet().stream()
            .filter(k -> !usedKeys.contains(k))
            .toList();

        String trimmed = input.stripTrailing();

        if (trimmed.isEmpty())
            return bare(JsonState.Mode.OPEN_BRACE, 0, remainingKeys, usedKeys);
        if (trimmed.equals("{"))
            return bare(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys);
        if (isFullyClosed(trimmed))
            return bare(JsonState.Mode.NONE, trimmed.length(), remainingKeys, usedKeys);
        if (topLevelEndsWithComma(trimmed))
            return bare(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys);
        if (topLevelCompletedPrimitive(trimmed))
            return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);
        if (topLevelCompletedQuotedString(trimmed))
            return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);

        Matcher insideObjectList = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*\\[(.*)$", Pattern.DOTALL
        ).matcher(trimmed);
        if (insideObjectList.find()) {
            FieldInfo topInfo = fields.get(insideObjectList.group(1));
            if (topInfo != null && topInfo.valueMode() == JsonState.Mode.LIST_OBJECT)
                return analyzeObjectListArray(trimmed, insideObjectList.group(2), topInfo, remainingKeys, usedKeys);
        }

        Matcher insideList = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*\\[([^]\\[]*)$"
        ).matcher(trimmed);
        if (insideList.find()) {
            FieldInfo info = fields.get(insideList.group(1));
            if (info != null && info.valueMode() == JsonState.Mode.LIST_VALUE)
                return analyzeSimpleListArray(trimmed, insideList.group(2), info, remainingKeys, usedKeys);
        }

        if (Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[[^]]*]\\s*$").matcher(trimmed).find())
            return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);

        Matcher afterColon = Pattern.compile("\"([^\"]+)\"\\s*:\\s*$").matcher(trimmed);
        if (afterColon.find()) {
            FieldInfo info = fields.get(afterColon.group(1));
            if (info != null)
                return new JsonState(info.valueMode(), trimmed.length(),
                    remainingKeys, usedKeys, Set.of(), null, info);
        }

        Matcher midBool = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(t|tr|tru|f|fa|fal|fals)$"
        ).matcher(trimmed);
        if (midBool.find()) {
            FieldInfo info = fields.get(midBool.group(1));
            if (info != null && info.valueMode() == JsonState.Mode.BOOL_VALUE)
                return new JsonState(JsonState.Mode.BOOL_VALUE, valueTokenStart(trimmed),
                    remainingKeys, usedKeys, Set.of(), null, info);
        }

        Matcher midNumber = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(-?[0-9]*\\.?[0-9]+)$"
        ).matcher(trimmed);
        if (midNumber.find()) {
            FieldInfo info = fields.get(midNumber.group(1));
            if (info != null && (info.valueMode() == JsonState.Mode.FLOAT_VALUE
                || info.valueMode() == JsonState.Mode.INT_VALUE))
                return new JsonState(info.valueMode(),
                    trimmed.length() - midNumber.group(2).length(),
                    remainingKeys, usedKeys, Set.of(), null, info);
        }

        Matcher midQuoted = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)$").matcher(trimmed);
        if (midQuoted.find()) {
            FieldInfo info = fields.get(midQuoted.group(1));
            if (info != null)
                return new JsonState(info.valueMode(), trimmed.lastIndexOf('"'),
                    remainingKeys, usedKeys, Set.of(), null, info);
        }

        if (Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"[^\"]+\"\\s*$").matcher(trimmed).find())
            return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);

        Matcher completedIdentifier = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*[a-z0-9_.-]+:[a-z0-9_./-]+\\s*$"
        ).matcher(trimmed);
        if (completedIdentifier.find()) {
            String key = completedIdentifier.group(1);
            FieldInfo info = fields.get(key);
            if (info != null && info.valueMode() == JsonState.Mode.IDENTIFIER_VALUE)
                return bare(JsonState.Mode.COMMA_OR_CLOSE, trimmed.length(), remainingKeys, usedKeys);
        }

        Matcher midIdentifier = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*([a-z0-9_.-]*:?[a-z0-9_./-]*)$"
        ).matcher(trimmed);
        if (midIdentifier.find()) {
            String token = midIdentifier.group(2);
            FieldInfo info = fields.get(midIdentifier.group(1));
            if (info != null && info.valueMode() == JsonState.Mode.IDENTIFIER_VALUE && !token.isEmpty())
                return new JsonState(JsonState.Mode.IDENTIFIER_VALUE,
                    trimmed.length() - token.length(),
                    remainingKeys, usedKeys, Set.of(), null, info);
        }

        if (trimmed.matches(".*\"[^\"]+\"\\s*$") && lastKeyHasNoColon(trimmed))
            return bare(JsonState.Mode.COLON, trimmed.length(), remainingKeys, usedKeys);

        Matcher midKey = Pattern.compile("(?:\\{|,)\\s*\"([^\"]*)$").matcher(trimmed);
        if (midKey.find())
            return bare(JsonState.Mode.KEY, trimmed.lastIndexOf('"'), remainingKeys, usedKeys);

        if (Pattern.compile("(?:\\{|,)\\s*$").matcher(trimmed).find())
            return bare(JsonState.Mode.KEY, trimmed.length(), remainingKeys, usedKeys);

        return bare(JsonState.Mode.NONE, input.length(), remainingKeys, usedKeys);
    }

    record JsonState(
        Mode mode,
        int suggestionStart,
        List<String> availableKeys,
        Set<String> usedKeys,
        Set<String> usedEntryKeys,
        @Nullable String currentEntryKey,
        @Nullable FieldInfo currentField
    ) {
        public enum Mode {
            OPEN_BRACE,
            KEY,
            COLON,
            BOOL_VALUE,
            INT_VALUE,
            FLOAT_VALUE,
            STRING_VALUE,
            IDENTIFIER_VALUE,
            LIST_VALUE,
            LIST_ELEMENT,
            LIST_COMMA_OR_CLOSE,
            LIST_OBJECT,
            LIST_OBJECT_OPEN,
            LIST_OBJECT_KEY,
            LIST_OBJECT_VALUE,
            LIST_OBJECT_COMMA_OR_CLOSE,
            ARRAY_COMMA_OR_CLOSE,
            COMMA_OR_CLOSE,
            CLOSE_BRACE,
            NONE
        }
    }

    record FieldInfo(
        JsonState.Mode valueMode,
        List<String> examples,
        @Nullable FieldInfo elementType,
        @Nullable Function<CommandContext<CommandSourceStack>, Iterable<Identifier>> identifierSupplier,
        @Nullable Function<CommandContext<CommandSourceStack>, List<String>> dynamicExamples,
        @Nullable Map<String, FieldInfo> entryFields
    ) {

        @Contract(" -> new")
        public static @NonNull FieldInfo bool() {
            return new FieldInfo(JsonState.Mode.BOOL_VALUE, List.of(), null, null, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo floatField(String... examples) {
            return new FieldInfo(JsonState.Mode.FLOAT_VALUE, List.of(examples), null, null, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo intField(String... examples) {
            return new FieldInfo(JsonState.Mode.INT_VALUE, List.of(examples), null, null, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo stringField(String... examples) {
            return new FieldInfo(JsonState.Mode.STRING_VALUE, List.of(examples), null, null, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo dynamicStringField(
            Function<CommandContext<CommandSourceStack>, List<String>> examples
        ) {
            return new FieldInfo(JsonState.Mode.STRING_VALUE, List.of(), null, null, examples, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo identifierField(
            Function<CommandContext<CommandSourceStack>, Iterable<Identifier>> supplier
        ) {
            return new FieldInfo(JsonState.Mode.IDENTIFIER_VALUE, List.of(), null, supplier, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo listField(@NonNull FieldInfo elementType) {
            return new FieldInfo(JsonState.Mode.LIST_VALUE, List.of(), elementType, null, null, null);
        }

        @Contract("_ -> new")
        public static @NonNull FieldInfo objectListField(@NonNull Map<String, FieldInfo> entryFields) {
            return new FieldInfo(JsonState.Mode.LIST_OBJECT, List.of(), null, null, null, entryFields);
        }

        public List<String> resolveExamples(CommandContext<CommandSourceStack> context) {
            if (dynamicExamples != null) return dynamicExamples.apply(context);
            return examples;
        }
    }
}
