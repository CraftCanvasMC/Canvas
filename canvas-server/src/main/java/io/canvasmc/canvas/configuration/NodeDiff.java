package io.canvasmc.canvas.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

public class NodeDiff {

    private final List<Change> changes;
    // we need char limit for when we inject comments
    private final int commentCharLim;

    // for iterator
    private int cursor = 0;

    private NodeDiff(final List<Change> changes, final int wrapLimit) {
        this.changes = changes;
        this.commentCharLim = wrapLimit;
    }

    public static NodeDiff compute(
        final Node fileNode,
        final Node objectNode,
        final Class<?> objectClass,
        final int commentCharLim
    ) {
        if (!(fileNode instanceof MappingNode fileMappingNode)) {
            throw new UnsupportedOperationException("File node must be a MappingNode, found " + fileNode.getClass().getSimpleName());
        }
        if (!(objectNode instanceof MappingNode objectMappingNode)) {
            throw new UnsupportedOperationException("Object node must be a MappingNode, found " + objectNode.getClass().getSimpleName());
        }

        // build token tree once upfront so we have comments for all depths
        final List<Token> tokens = Token.buildTree(objectClass);
        final List<Change> changes = new ArrayList<>();

        collectChanges(fileMappingNode, objectMappingNode, "", changes, tokens);
        return new NodeDiff(changes, commentCharLim);
    }

    public boolean hasNext() {
        return cursor < changes.size();
    }

    public int size() {
        return changes.size();
    }

    public List<Change> all() {
        return List.copyOf(changes);
    }

    public void applyNext(final Consumer<String> onAdd, final Consumer<String> onRemove) {
        if (!hasNext()) {
            throw new IllegalStateException("No more changes to apply");
        }

        final Change change = changes.get(cursor++);
        applyChange(change, commentCharLim);

        switch (change.type()) {
            case ADD -> onAdd.accept(change.fullyQualifiedName());
            case REMOVE -> onRemove.accept(change.fullyQualifiedName());
        }
    }

    public void applyAll(final Consumer<String> onAdd, final Consumer<String> onRemove) {
        while (hasNext()) {
            applyNext(onAdd, onRemove);
        }
    }

    static Map<String, NodeTuple> indexByKey(final MappingNode mapping) {
        final Map<String, NodeTuple> index = new LinkedHashMap<>();
        for (final NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode scalar) {
                index.put(scalar.getValue(), tuple);
            }
        }
        return index;
    }

    private static void collectChanges(
        final MappingNode fileMappings,
        final MappingNode objectMappings,
        final String prefix,
        final List<Change> out,
        final List<Token> tokens
    ) {
        final Map<String, NodeTuple> fileKeys = indexByKey(fileMappings);
        final Map<String, NodeTuple> objectKeys = indexByKey(objectMappings);

        // index tokens by name for comment lookup
        final Map<String, Token> tokenByName = new LinkedHashMap<>();
        for (final Token token : tokens) {
            tokenByName.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        // in obj, not file, so we mark for add
        for (final Map.Entry<String, NodeTuple> entry : objectKeys.entrySet()) {
            final String key = entry.getKey();
            final String fqn = fqn(prefix, key);

            final Token token = tokenByName.get(key);
            final List<Token> childTokens = token != null ? token.children() : List.of();
            final Style style = token != null ? token.style() : null;

            if (!fileKeys.containsKey(key)) {
                out.add(new Change(Type.ADD, fqn, entry.getValue(), fileMappings, style, childTokens));
            }
            else {
                final Node fileValue = fileKeys.get(key).getValueNode();
                final Node objectValue = entry.getValue().getValueNode();

                if (objectValue instanceof MappingNode om) {
                    // file side might be null/scalar if the section existed but was empty
                    if (fileValue instanceof MappingNode fm) {
                        collectChanges(fm, om, fqn, out, childTokens);
                    }
                    else {
                        // file has the key but not as a mapping, treat entire section as replaced
                        // remove the old scalar/null entry and add the full object subtree
                        out.add(new Change(Type.REMOVE, fqn, null, fileMappings, null, childTokens));
                        out.add(new Change(Type.ADD, fqn, entry.getValue(), fileMappings, style, childTokens));
                    }
                }
            }
        }

        // in file, not obj, so we mark for remove
        for (final Map.Entry<String, NodeTuple> entry : fileKeys.entrySet()) {
            final String key = entry.getKey();
            final String fqn = fqn(prefix, key);

            if (!objectKeys.containsKey(key)) {
                // we pass null for comment and child tokens because we don't read or care about these
                out.add(new Change(Type.REMOVE, fqn, null, fileMappings, null, null));
            }
        }
    }

    private static void applyChange(final Change change, final int wrapLimit) {
        final List<NodeTuple> tuples = change.fileParent().getValue();

        switch (change.type()) {
            case ADD -> {
                final NodeTuple tuple = change.objectTuple();

                // just to be sure that we aren't gonna do a stupid
                if (tuple == null) {
                    throw new IllegalStateException("wtf");
                }

                // inject comment on the key node itself
                if (change.style() != null && tuple.getKeyNode() instanceof ScalarNode keyNode) {
                    keyNode.setBlockComments(Token.compileStyle(change.style(), wrapLimit));
                }

                // recursively inject comments into any nested mapping children
                if (tuple.getValueNode() instanceof MappingNode nestedMapping && change.childTokens() != null) {
                    injectCommentsIntoSubtree(nestedMapping, change.childTokens(), wrapLimit);
                }

                // finished injecting comments, add to tuples
                tuples.add(tuple);
            }
            case REMOVE -> {
                final String targetKey = lastName(change.fullyQualifiedName());
                tuples.removeIf(tuple -> {
                    if (!(tuple.getKeyNode() instanceof ScalarNode scalar)) return false;
                    return scalar.getValue().equals(targetKey);
                });
            }
        }
    }

    private static void injectCommentsIntoSubtree(
        final MappingNode mapping,
        final List<Token> tokens,
        final int wrapLimit
    ) {
        final Map<String, Token> name2Token = new LinkedHashMap<>();
        for (final Token token : tokens) {
            name2Token.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        for (final NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) continue;

            final Token token = name2Token.get(keyNode.getValue());
            if (token == null) continue;

            if (token.style() != null) {
                keyNode.setBlockComments(Token.compileStyle(token.style(), wrapLimit));
            }

            // recurse if this child is itself a nested mapping too
            if (!token.children().isEmpty() && tuple.getValueNode() instanceof MappingNode nestedMapping) {
                injectCommentsIntoSubtree(nestedMapping, token.children(), wrapLimit);
            }
        }
    }

    private static String fqn(final String prefix, final String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static String lastName(final String fqn) {
        final int dot = fqn.lastIndexOf('.');
        return dot == -1 ? fqn : fqn.substring(dot + 1);
    }

    public enum Type {
        ADD,
        REMOVE
    }

    public record Change(
        NodeDiff.Type type,
        String fullyQualifiedName,
        @Nullable NodeTuple objectTuple,
        MappingNode fileParent,
        @Nullable Style style,
        @Nullable List<Token> childTokens
    ) {}
}
