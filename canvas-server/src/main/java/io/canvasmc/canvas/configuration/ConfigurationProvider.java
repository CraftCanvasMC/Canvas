package io.canvasmc.canvas.configuration;

import com.mojang.logging.LogUtils;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class ConfigurationProvider {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final LoaderOptions LOADER_OPTIONS;
    private static final DumperOptions DUMPER_OPTIONS;
    // note: if we want to preserve comments, we have to use compose() on file reads
    private static final Yaml YAML;

    static {
        LOADER_OPTIONS = new LoaderOptions();
        LOADER_OPTIONS.setProcessComments(true);
        LOADER_OPTIONS.setEnumCaseSensitive(false);

        DUMPER_OPTIONS = new DumperOptions();
        DUMPER_OPTIONS.setProcessComments(true);
        DUMPER_OPTIONS.setPrettyFlow(true);
        DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        DUMPER_OPTIONS.setIndent(3); // default is 2

        final FieldOrderPropertyUtils propertyUtils = new FieldOrderPropertyUtils();

        // from testing, it seems like these need to be set to make comments work
        // we also define a custom property utils so the order of the fields is
        // defined by the declaration order of the fields in the class

        final Representer representer = new Representer(DUMPER_OPTIONS) {
            private boolean representingKey = false;

            @Contract("_, _, _ -> new")
            @Override
            protected MappingNode representMapping(final Tag tag, final Map<?, ?> mapping, final DumperOptions.FlowStyle flowStyle) {
                // temporarily wrap representData so we can toggle the flag
                final List<NodeTuple> tuples = new ArrayList<>();
                for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                    representingKey = true;
                    final Node keyNode = representData(entry.getKey());
                    representingKey = false;
                    final Node valueNode = representData(entry.getValue());
                    tuples.add(new NodeTuple(keyNode, valueNode));
                }
                return new MappingNode(tag, tuples, flowStyle);
            }

            @Contract("_, _ -> new")
            @Override
            protected MappingNode representJavaBean(final Set<Property> properties, final Object javaBean) {
                final List<NodeTuple> tuples = new ArrayList<>();
                for (Property property : properties) {
                    representingKey = true;
                    final Node keyNode = representData(property.getName());
                    representingKey = false;
                    final Node valueNode = representData(property.get(javaBean));
                    tuples.add(new NodeTuple(keyNode, valueNode));
                }
                final Tag tag = getTag(javaBean.getClass(), Tag.MAP);
                return new MappingNode(tag, tuples, DUMPER_OPTIONS.getDefaultFlowStyle());
            }

            @Override
            protected Node representScalar(final Tag tag, final String value, DumperOptions.ScalarStyle style) {
                if (!representingKey && tag.equals(Tag.STR)) {
                    style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
                }
                return super.representScalar(tag, value, style);
            }

            @Override
            protected Tag getTag(final Class<?> clazz, final Tag defaultTag) {
                if (clazz.isEnum()) {
                    return Tag.STR;
                }
                return super.getTag(clazz, defaultTag);
            }
        };
        representer.setPropertyUtils(propertyUtils);
        representer.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);

        final Constructor constructor = new Constructor(LOADER_OPTIONS);
        constructor.setPropertyUtils(propertyUtils);
        constructor.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);

        YAML = new Yaml(
            constructor,
            representer,
            DUMPER_OPTIONS,
            LOADER_OPTIONS
        );
    }

    public static <C extends Part> void buildSolidConfiguration(
        final Path absolutePath,
        final Supplier<C> defaultSupplier,
        final int wrapLimit,
        final Resolver<C> resolver,
        final String... header
    ) {
        final C defaultObj = defaultSupplier.get();

        // if it doesn't exist, flood fill
        if (!Files.exists(absolutePath)) {
            floodFill(absolutePath, wrapLimit, resolver, defaultObj, header);
            return;
        }

        // so basically we have a file, so we should check first if there are differences
        // and then apply, then when writing a new file we tokenize and apply comments

        try {
            // note: the file representation should have comments tied already due to how we
            //       configured our constructor/representer, so for new comments we should just
            //       take the object representation and tokenize it, pull the added configs,
            //       and then inject those comments into the new file representation nodes
            final Node fileRepresentation = composeFromFile(absolutePath);
            final Node objectRepresentation = YAML.represent(defaultObj);

            // file representation can be null if the user completely empties the config
            if (fileRepresentation == null) {
                floodFill(absolutePath, wrapLimit, resolver, defaultObj, header);
                return;
            }

            // build and apply diff
            NodeDiff nodeDiff = NodeDiff.compute(fileRepresentation, objectRepresentation, defaultObj.getClass(), wrapLimit);
            while (nodeDiff.hasNext()) {
                nodeDiff.applyNext(resolver::onDiffAdd, resolver::onDiffRemove);
            }

            // with the diff applied to the file representation now, we need to write it
            // after writing, we need to rebuild the config file based off the new file

            // we need to set this, or it will include a global tag
            fileRepresentation.setTag(Tag.MAP);

            try {
                write(absolutePath, fileRepresentation, true, header);
            } catch (final IOException ioe) {
                throw new RuntimeException("Couldn't save config", ioe);
            }

            // we load the file that was just written, since with the diff applied this should
            // parse pretty perfectly now too, with no extra or missing keys

            //noinspection unchecked
            C userMade = (C) YAML.loadAs(new FileReader(absolutePath.toFile()), defaultObj.getClass());

            // finished load, call resolver and return
            resolver.onFinishLoad(userMade);
        } catch (final FileNotFoundException fnfe) {
            throw new IllegalStateException("File wasn't found?", fnfe);
        }
    }

    public static <C extends Part> void buildPatchableConfiguration(
        final Path patchAbsolute,
        final Path baseAbsolute,
        final Supplier<C> defaultSupplier,
        final Resolver<C> resolver,
        final String... header
    ) {
        // parse both files, create a diff, find what the patch overrides,
        // apply to the base, return modified version

        // so we need to check if the ORIGINAL exists, and if it doesn't then we throw
        if (!Files.exists(baseAbsolute)) {
            throw new IllegalStateException("Patch default needs to be present already. Use \"buildSolidConfiguration\" to create, then create a patch with this");
        }

        // load the base config as the starting point
        final C base;
        try {
            //noinspection unchecked
            base = (C) YAML.loadAs(new FileReader(baseAbsolute.toFile()), defaultSupplier.get().getClass());
        } catch (final FileNotFoundException fnfe) {
            throw new IllegalStateException("Base config disappeared between existence check and load", fnfe);
        }

        // check if the patch exists. if not, flood
        if (!Files.exists(patchAbsolute)) {
            // write just the header comment, no keys, the patch is intentionally empty by default
            try {
                Files.createDirectories(patchAbsolute.getParent());
                try (final FileWriter fw = new FileWriter(patchAbsolute.toFile())) {
                    // strip and write to the file
                    for (final String str : header) {
                        fw.write("# " + str + "\n");
                    }
                    fw.write("\n");
                }
            } catch (final IOException ioe) {
                throw new RuntimeException("Couldn't write patch file", ioe);
            }

            // the patch doesn't exist, so it is guaranteed to be the default
            resolver.onFinishLoad(base);
            return;
        }

        // the patch DOES exist, so we need to check for any options. if no options
        // are defined in the patch, we just return the default. otherwise, we patch the values

        try {
            final Node patchNode = composeFromFile(patchAbsolute);

            // null or non-mapping means the patch is empty, just return base
            if (patchNode == null) {
                resolver.onFinishLoad(base);
                return;
            }

            if (!(patchNode instanceof MappingNode patchMapping)) {
                throw new UnsupportedOperationException("Patch was not MappingNode, found " + patchNode.getClass().getSimpleName());
            }

            // represent the base object as a node tree so we can merge into it
            final Node baseNode = represent(base);

            if (!(baseNode instanceof MappingNode baseMapping)) {
                throw new UnsupportedOperationException("Base node was not MappingNode");
            }

            // deep-merge patch onto base node
            mergeNodes(baseMapping, patchMapping, "");

            // serialize the merged node back to a string and load it as C
            final StringWriter sw = new StringWriter();
            YAML.serialize(baseNode, sw);

            //noinspection unchecked
            final C merged = (C) YAML.loadAs(new StringReader(sw.toString()), defaultSupplier.get().getClass());

            resolver.onFinishLoad(merged);
        } catch (final FileNotFoundException fnfe) {
            throw new RuntimeException("Unable to find patch file", fnfe);
        }
    }

    static Node represent(final Part configPart) {
        final Node node = YAML.represent(configPart);
        node.setTag(Tag.MAP);
        return node;
    }

    static void mergeNodes(
        final MappingNode baseMappings,
        final MappingNode patchMappings,
        final String prefix
    ) {
        final Map<String, NodeTuple> baseKeys = NodeDiff.indexByKey(baseMappings);
        final Map<String, NodeTuple> patchKeys = NodeDiff.indexByKey(patchMappings);

        for (final Map.Entry<String, NodeTuple> patchEntry : patchKeys.entrySet()) {
            final String key = patchEntry.getKey();
            final String fqn = prefix.isEmpty() ? key : prefix + "." + key;

            if (!baseKeys.containsKey(key)) {
                // patch references a key that no longer exists in the base — skip
                LOGGER.warn("Patch key '{}' does not exist in base config, skipping", fqn);
                continue;
            }

            final Node baseValue = baseKeys.get(key).getValueNode();
            final Node patchValue = patchEntry.getValue().getValueNode();

            if (baseValue instanceof MappingNode bm && patchValue instanceof MappingNode pm) {
                // both are sections, recurse rather than replacing the whole section
                mergeNodes(bm, pm, fqn);
            }
            else {
                final List<NodeTuple> baseTuples = baseMappings.getValue();

                // scalar or sequence, swap the base tuple out for the patch tuple
                baseTuples.replaceAll(tuple -> {
                    if (!(tuple.getKeyNode() instanceof ScalarNode sk)) return tuple;
                    if (!sk.getValue().equals(key)) return tuple;
                    // keep the base key node but take the patch's value node
                    return new NodeTuple(tuple.getKeyNode(), patchValue);
                });

                LOGGER.debug("Patch applied override for '{}'", fqn);
            }
        }
    }

    /**
     * Returns the YAML serialized {@link org.yaml.snakeyaml.nodes.Node} instance from a file, or null if the
     * configuration is completely emptied
     *
     * @param absolutePath
     *     the path of the file
     *
     * @return the compiled node, or {@code null} if the configuration is completely empty
     *
     * @throws FileNotFoundException
     *     if the file doesn't exist
     */
    @Nullable
    static Node composeFromFile(final Path absolutePath) throws FileNotFoundException {
        return YAML.compose(new FileReader(absolutePath.toFile()));
    }

    private static <C extends Part> void floodFill(
        final Path absolutePath,
        final int wrapLimit,
        final @Nullable Resolver<C> resolver,
        final C defaultObject,
        final String... header
    ) {
        LOGGER.info("{} doesn't exist, using flood fill", absolutePath.getFileName());

        final Node defaultRepresentation = YAML.represent(defaultObject);
        Token.injectComments(defaultObject.getClass(), defaultRepresentation, wrapLimit);

        // we need to set this, or it will include a global tag
        defaultRepresentation.setTag(Tag.MAP);

        try {
            write(absolutePath, defaultRepresentation, false, header);
        } catch (final IOException ioe) {
            throw new RuntimeException("Couldn't save config", ioe);
        }

        // finished load, call resolver and return
        if (resolver != null) {
            // resolver CAN be null, the only time this happens is when the
            // caller wants to handle this themsleves
            resolver.onFinishLoad(defaultObject);
        }

        // we don't have to do anything about trying to patch this, no file was present, return
    }

    protected static void write(
        final Path absolutePath, final Node representation, final boolean alreadyExisted, final String... header
    ) throws IOException {
        // only write the header on first creation, because if the file already existed,
        // the user may have edited or removed it intentionally, so we never touch it
        if (!alreadyExisted && header.length > 0) {
            // the header is special, it can define its own lines and its own formatting
            final List<CommentLine> lines = new LinkedList<>();
            for (final String str : header) {
                Objects.requireNonNull(str, "Header line cannot be null");
                // we specifically do not let the token system format this, we trust the
                // header is formatted literally and to the extent the user wants
                lines.add(Token.toCommentLine(str));
            }
            // add blank line so that it's kinda separated from the other comments
            lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            // don't want to override existing comments completely
            if (representation.getBlockComments() != null) {
                lines.addAll(representation.getBlockComments());
            }
            representation.setBlockComments(lines);
        }

        // now that we wrote the header, write to file
        Files.createDirectories(absolutePath.getParent());
        try (final FileWriter fw = new FileWriter(absolutePath.toFile())) {
            serialize(representation, fw);
        }
    }

    protected static void serialize(final Node representation, final Writer fw) {
        YAML.serialize(representation, fw);
    }
}
