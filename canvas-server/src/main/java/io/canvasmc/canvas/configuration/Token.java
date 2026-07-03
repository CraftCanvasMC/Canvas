package io.canvasmc.canvas.configuration;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

// note: if parent is null, it is in root
public record Token(
    @Nullable Token parent, List<Token> children, String name, @Nullable Style style
) {

    // note: this is a linked list ordered by class field order
    public static List<Token> buildTree(final Class<?> clazz) {
        if (!Part.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Config class " + clazz.getName() + " must extend Part");
        }

        //noinspection unchecked
        final Class<? extends Part> partClass = (Class<? extends Part>) clazz;
        final Map<String, Part.OptionDefinition> styles = Part.harvest(partClass);
        final List<Token> tokens = new LinkedList<>();

        for (final Field declaredField : clazz.getDeclaredFields()) {
            // skip any final fields
            if (declaredField.accessFlags().contains(AccessFlag.FINAL)) {
                continue;
            }

            tokens.add(compile(declaredField, null, partClass, styles));
        }

        return tokens;
    }

    // all generated tokens from the class should be present in the node representation
    public static void injectComments(final Class<?> aClass, final Node representation, final int wrapLimit) {
        List<Token> tokens = buildTree(aClass);
        if (!(representation instanceof MappingNode mappingNode))
            throw new UnsupportedOperationException("Nodes that are not instanceof MappingNode are not supported");
        injectIntoMapping(tokens, mappingNode, wrapLimit);
    }

    static CommentLine toCommentLine(final String text) {
        return new CommentLine(null, null, text.isEmpty() ? "" : " " + text, CommentType.BLOCK);
    }

    static List<CommentLine> compileStyle(final Style style, final int wrapLimit) {
        List<CommentLine> lines = new ArrayList<>();
        for (String line : style.compile(wrapLimit)) {
            lines.add(toCommentLine(line));
        }
        return lines;
    }

    // kept for ConfigurationProvider header usage
    static List<CommentLine> buildCommentLines(final String rawComment, final int wrapLimit) {
        final List<CommentLine> lines = new ArrayList<>();
        appendWordWrapped(rawComment, wrapLimit, lines);
        return lines;
    }

    @Contract("_, _, _, _ -> new")
    private static Token compile(
        final Field field,
        final @Nullable Token parent,
        final Class<? extends Part> insideClass,
        final Map<String, Part.OptionDefinition> parentStyles
    ) {
        final Part.OptionDefinition definition = parentStyles.get(field.getName());
        final String name = field.getName();
        final List<Token> children = new LinkedList<>();
        final Class<?> fieldType = field.getType();

        // create the head token first
        final Token token = new Token(parent, children, name, definition.commentStyle());

        // check if the field type is a nested Part subclass
        for (final Class<?> nested : insideClass.getDeclaredClasses()) {
            if (nested.equals(fieldType) && Part.class.isAssignableFrom(nested)) {
                //noinspection unchecked
                final Class<? extends Part> nestedPart = (Class<? extends Part>) nested;
                final Map<String, Part.OptionDefinition> nestedStyles = Part.harvest(nestedPart);

                // build children based on this
                for (final Field nestedField : nested.getDeclaredFields()) {
                    if (nestedField.accessFlags().contains(AccessFlag.FINAL)) {
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
        final List<Token> tokens,
        final MappingNode mappingNode,
        final int wrapLimit
    ) {
        final Map<String, Token> name2Token = new LinkedHashMap<>();
        for (final Token token : tokens) {
            name2Token.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        for (final NodeTuple tuple : mappingNode.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) continue;

            final String key = keyNode.getValue();
            final Token token = name2Token.get(key);
            if (token == null) continue;

            // inject style comment onto this key node if one exists
            if (token.style() != null) {
                keyNode.setBlockComments(compileStyle(token.style(), wrapLimit));
            }

            // recurse into nested mappings if this token has children
            if (!token.children().isEmpty() && tuple.getValueNode() instanceof MappingNode nestedMapping) {
                injectIntoMapping(token.children(), nestedMapping, wrapLimit);
            }
        }
    }

    // wraps a single block of text into comment lines
    private static void appendWordWrapped(
        final String text,
        final int wrapLimit,
        final List<CommentLine> out
    ) {
        final String[] words = text.split("\\s+");
        final StringBuilder currentLine = new StringBuilder();

        for (final String word : words) {
            if (word.isEmpty()) continue;

            final boolean hasContent = !currentLine.isEmpty();
            final int projected = hasContent
                ? currentLine.length() + 1 + word.length()
                : word.length();

            if (hasContent && projected > wrapLimit) {
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
}
