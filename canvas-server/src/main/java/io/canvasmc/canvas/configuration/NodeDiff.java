package io.canvasmc.canvas.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
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

    private static void collectChanges(
        final @NonNull MappingNode fileMapping,
        final @NonNull MappingNode objectMapping,
        final @NonNull String prefix,
        final @NonNull List<Change> out,
        final @NonNull List<Token> tokens
    ) {
        Map<String, NodeTuple> fileKeys = indexByKey(fileMapping);
        Map<String, NodeTuple> objectKeys = indexByKey(objectMapping);

        // index tokens by name for comment lookup
        Map<String, Token> tokenByName = new LinkedHashMap<>();
        for (Token token : tokens) {
            tokenByName.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        // in obj, not file, so we mark for add
        for (Map.Entry<String, NodeTuple> entry : objectKeys.entrySet()) {
            String key = entry.getKey();
            String fqn = fqn(prefix, key);

            Token token = tokenByName.get(key);
            List<Token> childTokens = token != null ? token.children() : List.of();
            String comment = token != null ? token.comment() : null;

            if (!fileKeys.containsKey(key)) {
                out.add(new Change(Type.ADD, fqn, entry.getValue(), fileMapping, comment, childTokens));
            }
            else {
                Node fileValue = fileKeys.get(key).getValueNode();
                Node objectValue = entry.getValue().getValueNode();

                if (objectValue instanceof MappingNode om) {
                    // file side might be null/scalar if the section existed but was empty
                    if (fileValue instanceof MappingNode fm) {
                        collectChanges(fm, om, fqn, out, childTokens);
                    }
                    else {
                        // file has the key but not as a mapping, treat entire section as replaced
                        // remove the old scalar/null entry and add the full object subtree
                        out.add(new Change(Type.REMOVE, fqn, null, fileMapping, null, childTokens));
                        out.add(new Change(Type.ADD, fqn, entry.getValue(), fileMapping, comment, childTokens));
                    }
                }
            }
        }

        // in file, not obj, so we mark for remove
        for (Map.Entry<String, NodeTuple> entry : fileKeys.entrySet()) {
            String key = entry.getKey();
            String fqn = fqn(prefix, key);

            if (!objectKeys.containsKey(key)) {
                // we pass null for comment and child tokens because we don't read or care about these
                out.add(new Change(Type.REMOVE, fqn, null, fileMapping, null, null));
            }
        }
    }

    private static void applyChange(final @NonNull Change change, final int commentCharLim) {
        List<NodeTuple> tuples = change.fileParent().getValue();

        switch (change.type()) {
            case ADD -> {
                NodeTuple tuple = change.objectTuple();

                // just to be sure that we aren't gonna do a stupid
                if (tuple == null) {
                    throw new IllegalStateException("wtf");
                }

                // inject comment on the key node itself
                if (change.comment() != null && tuple.getKeyNode() instanceof ScalarNode keyNode) {
                    keyNode.setBlockComments(Token.buildCommentLines(change.comment(), commentCharLim));
                }

                // recursively inject comments into any nested mapping children
                if (tuple.getValueNode() instanceof MappingNode nestedMapping && change.childTokens() != null) {
                    injectCommentsIntoSubtree(nestedMapping, change.childTokens(), commentCharLim);
                }

                // finished injecting comments, add to tuples
                tuples.add(tuple);
            }
            case REMOVE -> {
                String targetKey = lastName(change.fullyQualifiedName());
                tuples.removeIf(tuple -> {
                    if (!(tuple.getKeyNode() instanceof ScalarNode scalar)) return false;
                    return scalar.getValue().equals(targetKey);
                });
            }
        }
    }

    private static void injectCommentsIntoSubtree(
        final @NonNull MappingNode mapping,
        final @NonNull List<Token> tokens,
        final int commentCharLim
    ) {
        Map<String, Token> tokenByName = new LinkedHashMap<>();
        for (Token token : tokens) {
            tokenByName.put(FieldOrderPropertyUtils.toKebabCase(token.name()), token);
        }

        for (NodeTuple tuple : mapping.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) continue;

            Token token = tokenByName.get(keyNode.getValue());
            if (token == null) continue;

            if (token.comment() != null) {
                keyNode.setBlockComments(Token.buildCommentLines(token.comment(), commentCharLim));
            }

            // recurse if this child is itself a nested mapping too
            if (!token.children().isEmpty() && tuple.getValueNode() instanceof MappingNode nestedMapping) {
                injectCommentsIntoSubtree(nestedMapping, token.children(), commentCharLim);
            }
        }
    }

    private static @NonNull String fqn(final @NonNull String prefix, final @NonNull String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static @NonNull String lastName(final @NonNull String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot == -1 ? fqn : fqn.substring(dot + 1);
    }

    private NodeDiff(final @NonNull List<Change> changes, final int commentCharLim) {
        this.changes = changes;
        this.commentCharLim = commentCharLim;
    }

    static @NonNull Map<String, NodeTuple> indexByKey(final @NonNull MappingNode mapping) {
        Map<String, NodeTuple> index = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode scalar) {
                index.put(scalar.getValue(), tuple);
            }
        }
        return index;
    }

    public static @NonNull NodeDiff compute(
        final @NonNull Node fileNode,
        final @NonNull Node objectNode,
        final @NonNull Class<?> objectClass,
        final int commentCharLim
    ) {
        if (!(fileNode instanceof MappingNode fileMappingNode)) {
            throw new UnsupportedOperationException("File node must be a MappingNode, found " + fileNode.getClass().getSimpleName());
        }
        if (!(objectNode instanceof MappingNode objectMappingNode)) {
            throw new UnsupportedOperationException("Object node must be a MappingNode, found " + objectNode.getClass().getSimpleName());
        }

        // build token tree once upfront so we have comments for all depths
        List<Token> tokens = Token.buildTree(objectClass);

        List<Change> changes = new ArrayList<>();
        collectChanges(fileMappingNode, objectMappingNode, "", changes, tokens);
        return new NodeDiff(changes, commentCharLim);
    }

    public boolean hasNext() {
        return cursor < changes.size();
    }

    public int size() {
        return changes.size();
    }

    public @NonNull List<Change> all() {
        return List.copyOf(changes);
    }

    public void applyNext(
        final @NonNull Consumer<String> onAdd,
        final @NonNull Consumer<String> onRemove
    ) {
        if (!hasNext()) {
            throw new IllegalStateException("No more changes to apply");
        }

        Change change = changes.get(cursor++);
        applyChange(change, commentCharLim);

        switch (change.type()) {
            case ADD -> onAdd.accept(change.fullyQualifiedName());
            case REMOVE -> onRemove.accept(change.fullyQualifiedName());
        }
    }

    public void applyAll(
        final @NonNull Consumer<String> onAdd,
        final @NonNull Consumer<String> onRemove
    ) {
        while (hasNext()) {
            applyNext(onAdd, onRemove);
        }
    }

    public enum Type {
        ADD,
        REMOVE
    }

    public record Change(
        NodeDiff.@NonNull Type type,
        @NonNull String fullyQualifiedName,
        @Nullable NodeTuple objectTuple,
        @NonNull MappingNode fileParent,
        @Nullable String comment,
        @Nullable List<Token> childTokens
    ) {}
}
