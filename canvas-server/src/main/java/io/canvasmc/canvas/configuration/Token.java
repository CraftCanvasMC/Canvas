package io.canvasmc.canvas.configuration;

import io.canvasmc.canvas.configuration.markers.Comment;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import joptsimple.internal.Strings;
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
//       also, comments should be stripped of `\n` chars before being passed
//       here. the serializer will line up words and such on its own
public record Token(
    @Nullable Token parent, @NonNull List<Token> children, @NonNull String name, @Nullable String comment
) {
    @Contract("_, _, _ -> new")
    private static @NonNull Token compile(
        final @NonNull Field field,
        final @Nullable Token parent,
        final @NonNull Class<?> classInsideOf
    ) {
        String comment = field.isAnnotationPresent(Comment.class)
            ? Strings.join(field.getAnnotation(Comment.class).value(), "\n")
            : null;

        if (comment != null) {
            comment = comment.replace("\n", " ").replace("\r", " ").trim();
        }

        String name = field.getName();

        List<Token> children = new LinkedList<>();

        Token token = new Token(parent, children, name, comment);

        Class<?> fieldType = field.getType();

        // check if class type is nested
        for (Class<?> nested : classInsideOf.getDeclaredClasses()) {
            if (nested.equals(fieldType)) {

                // build children based on this
                for (Field nestedField : nested.getDeclaredFields()) {
                    if (
                        !nestedField.accessFlags().contains(AccessFlag.PUBLIC) ||
                            nestedField.accessFlags().contains(AccessFlag.FINAL)
                    ) {
                        continue;
                    }

                    Token child = compile(nestedField, token, nested);
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
            tokenByName.put(token.name(), token);
        }

        for (NodeTuple tuple : mappingNode.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) continue;

            String key = keyNode.getValue();
            Token token = tokenByName.get(key);
            if (token == null) continue;

            // inject comment onto this key node if one exists
            if (token.comment() != null) {
                keyNode.setBlockComments(buildCommentLines(token.comment(), commentCharLim));
            }

            // recurse into nested mappings if this token has children
            if (!token.children().isEmpty() && tuple.getValueNode() instanceof MappingNode nestedMapping) {
                injectIntoMapping(token.children(), nestedMapping, commentCharLim);
            }
        }
    }

    private static @NonNull CommentLine toCommentLine(final @NonNull String text) {
        return new CommentLine(null, null, " " + text, CommentType.BLOCK);
    }

    static @NonNull List<CommentLine> buildCommentLines(
        final @NonNull String rawComment,
        final int charLim
    ) {
        List<CommentLine> lines = new ArrayList<>();
        String[] words = rawComment.split("\\s+");

        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            boolean lineHasContent = !currentLine.isEmpty();

            // would adding this word exceed the limit?
            int projectedLength = lineHasContent
                ? currentLine.length() + 1 + word.length()
                : word.length();

            if (lineHasContent && projectedLength > charLim) {
                // flush current line, start a new one
                lines.add(toCommentLine(currentLine.toString()));
                currentLine.setLength(0);
                currentLine.append(word);
            }
            else {
                if (lineHasContent) currentLine.append(' ');
                currentLine.append(word);
            }
        }

        // flush the last line
        if (!currentLine.isEmpty()) {
            lines.add(toCommentLine(currentLine.toString()));
        }

        return lines;
    }

    // note: this is a linked list ordered by class field order
    public static @NonNull List<Token> buildTree(final @NonNull Class<?> clazz) {
        List<Token> tokens = new LinkedList<>();

        for (final Field declaredField : clazz.getDeclaredFields()) {
            // skip any non-public or final fields
            if (
                !declaredField.accessFlags().contains(AccessFlag.PUBLIC) ||
                    declaredField.accessFlags().contains(AccessFlag.FINAL)
            ) {
                continue;
            }

            tokens.add(compile(declaredField, null, clazz));
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
