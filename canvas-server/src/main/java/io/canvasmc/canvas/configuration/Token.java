package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

// note: if parent is null, it is in root
public record Token(
    @Nullable Token parent, @NonNull List<Token> children, @NonNull String name, @Nullable Style style
) {

    @Contract("_, _, _, _ -> new")
    private static @NonNull Token compile(
        final @NonNull Field field,
        final @Nullable Token parent,
        final @NonNull Class<? extends Part> classInsideOf,
        final @NonNull Map<String, Style> stylesOfParent
    ) {
        Style style = stylesOfParent.get(field.getName());
        String name = field.getName();

        List<Token> children = new LinkedList<>();

        Token token = new Token(parent, children, name, style);

        Class<?> fieldType = field.getType();

        // check if the field type is a nested Part subclass
        for (Class<?> nested : classInsideOf.getDeclaredClasses()) {
            if (nested.equals(fieldType) && Part.class.isAssignableFrom(nested)) {
                @SuppressWarnings("unchecked")
                Class<? extends Part> nestedPart = (Class<? extends Part>) nested;
                Map<String, Style> nestedStyles = Part.harvest(nestedPart);

                // build children based on this
                for (Field nestedField : nested.getDeclaredFields()) {
                    if (
                        !nestedField.accessFlags().contains(AccessFlag.PUBLIC) ||
                            nestedField.accessFlags().contains(AccessFlag.FINAL)
                    ) {
                        continue;
                    }

                    Token child = compile(nestedField, token, nestedPart, nestedStyles);
                    children.add(child);
                }

                break;
            }
        }

        return token;
    }

    private static void injectIntoMapping(
        final @NonNull List<Token> tokens,
        final @NonNull MappingNode mappingNode,
        final int commentCharLim
    ) {
        Map<String, Token> tokenByName = new LinkedHashMap<>();
        for (Token token : tokens) {
            tokenByName.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        for (NodeTuple tuple : mappingNode.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) continue;

            String key = keyNode.getValue();
            Token token = tokenByName.get(key);
            if (token == null) continue;

            // inject style comment onto this key node if one exists
            if (token.style() != null) {
                keyNode.setBlockComments(compileStyle(token.style(), commentCharLim));
            }

            // recurse into nested mappings if this token has children
            if (!token.children().isEmpty() && tuple.getValueNode() instanceof MappingNode nestedMapping) {
                injectIntoMapping(token.children(), nestedMapping, commentCharLim);
            }
        }
    }

    static @NonNull CommentLine toCommentLine(final @NonNull String text) {
        return new CommentLine(null, null, text.isEmpty() ? "" : " " + text, CommentType.BLOCK);
    }

    // wraps a single block of text into comment lines
    private static void appendWordWrapped(
        final @NonNull String text,
        final int charLim,
        final @NonNull List<CommentLine> out
    ) {
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            boolean hasContent = !currentLine.isEmpty();
            int projected = hasContent
                ? currentLine.length() + 1 + word.length()
                : word.length();

            if (hasContent && projected > charLim) {
                out.add(toCommentLine(currentLine.toString()));
                currentLine.setLength(0);
                currentLine.append(word);
            }
            else {
                if (hasContent) currentLine.append(' ');
                currentLine.append(word);
            }
        }

        // flush the last line
        if (!currentLine.isEmpty()) {
            out.add(toCommentLine(currentLine.toString()));
        }
    }

    static @NonNull List<CommentLine> compileStyle(
        final @NonNull Style style,
        final int charLim
    ) {
        List<CommentLine> lines = new ArrayList<>();
        for (String line : style.compile(charLim)) {
            lines.add(toCommentLine(line));
        }
        return lines;
    }

    // kept for ConfigurationProvider header usage
    static @NonNull List<CommentLine> buildCommentLines(
        final @NonNull String rawComment,
        final int charLim
    ) {
        List<CommentLine> lines = new ArrayList<>();
        appendWordWrapped(rawComment, charLim, lines);
        return lines;
    }

    // note: this is a linked list ordered by class field order
    public static @NonNull List<Token> buildTree(final @NonNull Class<?> clazz) {
        if (!Part.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Config class " + clazz.getName() + " must extend Part");
        }

        @SuppressWarnings("unchecked")
        Class<? extends Part> partClass = (Class<? extends Part>) clazz;
        Map<String, Style> styles = Part.harvest(partClass);

        List<Token> tokens = new LinkedList<>();

        for (final Field declaredField : clazz.getDeclaredFields()) {
            // skip any non-public or final fields
            if (
                !declaredField.accessFlags().contains(AccessFlag.PUBLIC) ||
                    declaredField.accessFlags().contains(AccessFlag.FINAL)
            ) {
                continue;
            }

            tokens.add(compile(declaredField, null, partClass, styles));
        }

        return tokens;
    }

    // all generated tokens from the class should be present in the node representation
    public static void injectComments(final Class<?> aClass, final Node representation, final int commentCharLim) {
        List<Token> tokens = buildTree(aClass);
        if (!(representation instanceof MappingNode mappingNode))
            throw new UnsupportedOperationException("Nodes that are not instanceof MappingNode are not supported");
        injectIntoMapping(tokens, mappingNode, commentCharLim);
    }
}
