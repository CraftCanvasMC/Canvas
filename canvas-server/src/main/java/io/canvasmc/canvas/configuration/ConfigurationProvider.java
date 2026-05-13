package io.canvasmc.canvas.configuration;

import com.mojang.logging.LogUtils;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
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

        FieldOrderPropertyUtils propertyUtils = new FieldOrderPropertyUtils();

        // from testing, it seems like these need to be set to make comments work
        // we also define a custom property utils so the order of the fields is
        // defined by the declaration order of the fields in the class

        Representer representer = new Representer(DUMPER_OPTIONS) {
            private boolean representingKey = false;

            @Contract("_, _, _ -> new")
            @Override
            protected @NonNull MappingNode representMapping(Tag tag, @NonNull Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
                // temporarily wrap representData so we can toggle the flag
                List<NodeTuple> tuples = new ArrayList<>();
                for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                    representingKey = true;
                    Node keyNode = representData(entry.getKey());
                    representingKey = false;
                    Node valueNode = representData(entry.getValue());
                    tuples.add(new NodeTuple(keyNode, valueNode));
                }
                return new MappingNode(tag, tuples, flowStyle);
            }

            @Contract("_, _ -> new")
            @Override
            protected @NonNull MappingNode representJavaBean(@NonNull Set<Property> properties, Object javaBean) {
                List<NodeTuple> tuples = new ArrayList<>();
                for (Property property : properties) {
                    representingKey = true;
                    Node keyNode = representData(property.getName());
                    representingKey = false;
                    Node valueNode = representData(property.get(javaBean));
                    tuples.add(new NodeTuple(keyNode, valueNode));
                }
                Tag tag = getTag(javaBean.getClass(), Tag.MAP);
                return new MappingNode(tag, tuples, DUMPER_OPTIONS.getDefaultFlowStyle());
            }

            @Override
            protected Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
                if (!representingKey && tag.equals(Tag.STR)) {
                    style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
                }
                return super.representScalar(tag, value, style);
            }

            @Override
            protected Tag getTag(@NonNull Class<?> clazz, Tag defaultTag) {
                if (clazz.isEnum()) {
                    return Tag.STR;
                }
                return super.getTag(clazz, defaultTag);
            }
        };
        representer.setPropertyUtils(propertyUtils);
        representer.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);

        Constructor constructor = new Constructor(LOADER_OPTIONS);
        constructor.setPropertyUtils(propertyUtils);
        constructor.getPropertyUtils().setBeanAccess(BeanAccess.FIELD);

        YAML = new Yaml(
            constructor,
            representer,
            DUMPER_OPTIONS,
            LOADER_OPTIONS
        );
    }

    private static <C extends Part> void floodFill(
        final @NonNull Path pathAbsolute,
        final int commentCharLim,
        final @Nullable Resolver<C> resolver,
        final C defaultObj,
        final @Nullable String[] header
    ) {
        LOGGER.info("{} doesn't exist, using flood fill", pathAbsolute.getFileName());

        Node representation = YAML.represent(defaultObj);
        Token.injectComments(defaultObj.getClass(), representation, commentCharLim);

        // we need to set this, or it will include a global tag
        representation.setTag(Tag.MAP);

        try {
            write(pathAbsolute, representation, header, false);
        } catch (IOException ioe) {
            throw new RuntimeException("Couldn't save config", ioe);
        }

        // finished load, call resolver and return
        if (resolver != null) {
            // resolver CAN be null, the only time this happens is when the
            // caller wants to handle this themsleves
            resolver.onFinishLoad(injectNode(defaultObj, representation));
        }

        // we don't have to do anything about trying to patch this, no file was present, return
    }

    @Contract("_, _ -> param1")
    private static <C extends Part> @NonNull C injectNode(final @NonNull C defaultObj, final Node node) {
        defaultObj.node.setValue(node);
        return defaultObj;
    }

    private static void mergeNodes(
        final @NonNull MappingNode baseMapping,
        final @NonNull MappingNode patchMapping,
        final @NonNull String prefix
    ) {
        Map<String, NodeTuple> baseKeys = NodeDiff.indexByKey(baseMapping);
        Map<String, NodeTuple> patchKeys = NodeDiff.indexByKey(patchMapping);

        for (Map.Entry<String, NodeTuple> patchEntry : patchKeys.entrySet()) {
            String key = patchEntry.getKey();
            String fqn = prefix.isEmpty() ? key : prefix + "." + key;

            if (!baseKeys.containsKey(key)) {
                // patch references a key that no longer exists in the base — skip
                LOGGER.warn("Patch key '{}' does not exist in base config, skipping", fqn);
                continue;
            }

            Node baseValue = baseKeys.get(key).getValueNode();
            Node patchValue = patchEntry.getValue().getValueNode();

            if (baseValue instanceof MappingNode bm && patchValue instanceof MappingNode pm) {
                // both are sections, recurse rather than replacing the whole section
                mergeNodes(bm, pm, fqn);
            }
            else {
                // scalar or sequence, swap the base tuple out for the patch tuple
                List<NodeTuple> baseTuples = baseMapping.getValue();
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

    protected static void write(
        final @NonNull Path pathAbsolute, final Node representation, final @Nullable String[] header, final boolean alreadyExisted
    ) throws IOException {
        // only write the header on first creation, because if the file already existed,
        // the user may have edited or removed it intentionally, so we never touch it
        if (!alreadyExisted && header != null) {
            // the header is special, it can define its own lines and its own formatting
            List<CommentLine> lines = new ArrayList<>();
            for (final String str : header) {
                if (str == null)
                    throw new IllegalArgumentException("Line in header must not be null. If you want a blank line, use an empty string");
                // we specifically do not let the token system format this, we trust the
                // header is formatted literally and to the extent the user wants
                lines.add(Token.toCommentLine(str));
            }
            // add blank line so that it's kinda separated from the other comments
            lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
            representation.setBlockComments(lines);
        }

        // now that we wrote the header, write to file
        Files.createDirectories(pathAbsolute.getParent());
        try (FileWriter fw = new FileWriter(pathAbsolute.toFile())) {
            YAML.serialize(representation, fw);
        }
    }

    public static <C extends Part> void buildSolidConfiguration(
        final Path pathAbsolute,
        final @NonNull Supplier<C> defaultSupplier,
        final int commentCharLim,
        final Resolver<C> resolver,
        final @Nullable String[] header
    ) {
        final C defaultObj = defaultSupplier.get();

        // if it doesn't exist, flood fill
        if (!Files.exists(pathAbsolute)) {
            floodFill(pathAbsolute, commentCharLim, resolver, defaultObj, header);
            return;
        }

        // so basically we have a file, so we should check first if there are differences
        // and then apply, then when writing a new file we tokenize and apply comments

        try {
            // note: the file representation should have comments tied already due to how we
            //       configured our constructor/representer, so for new comments we should just
            //       take the object representation and tokenize it, pull the added configs,
            //       and then inject those comments into the new file representation nodes
            Node fileRepresentation = YAML.compose(new FileReader(pathAbsolute.toFile()));
            Node objectRepresentation = YAML.represent(defaultObj);

            // file representation can be null if the user completely empties the config
            if (fileRepresentation == null) {
                floodFill(pathAbsolute, commentCharLim, resolver, defaultObj, header);
                return;
            }

            // build and apply diff
            NodeDiff nodeDiff = NodeDiff.compute(fileRepresentation, objectRepresentation, defaultObj.getClass(), commentCharLim);
            while (nodeDiff.hasNext()) {
                nodeDiff.applyNext(resolver::onDiffAdd, resolver::onDiffRemove);
            }

            // with the diff applied to the file representation now, we need to write it
            // after writing, we need to rebuild the config file based off the new file

            // we need to set this, or it will include a global tag
            fileRepresentation.setTag(Tag.MAP);

            try {
                write(pathAbsolute, fileRepresentation, header, true);
            } catch (IOException ioe) {
                throw new RuntimeException("Couldn't save config", ioe);
            }

            // we load the file that was just written, since with the diff applied this should
            // parse pretty perfectly now too, with no extra or missing keys

            //noinspection unchecked
            C userMade = (C) YAML.loadAs(new FileReader(pathAbsolute.toFile()), defaultObj.getClass());

            // finished load, call resolver and return
            resolver.onFinishLoad(injectNode(userMade, fileRepresentation));
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("File wasn't found?", e);
        }
    }

    public static <C extends Part> void buildPatchableConfiguration(
        final Path patchAbsolute,
        final Path baseAbsolute,
        final @NonNull Supplier<C> defaultSupplier,
        final Resolver<C> resolver,
        final String[] header
    ) {
        // parse both files, create a diff, find what the patch overrides,
        // apply to the base, return modified version

        // so we need to check if the ORIGINAL exists, and if it doesn't then we throw
        if (!Files.exists(baseAbsolute)) {
            throw new IllegalStateException("Patch default needs to be present already. Use 'buildSolidConfiguration' to create, then create a patch with this");
        }

        // load the base config as the starting point
        C base;
        try {
            //noinspection unchecked
            base = (C) YAML.loadAs(new FileReader(baseAbsolute.toFile()), defaultSupplier.get().getClass());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Base config disappeared between existence check and load", e);
        }

        // check if the patch exists. if not, flood
        if (!Files.exists(patchAbsolute)) {
            // write just the header comment, no keys, the patch is intentionally empty by default
            try {
                Files.createDirectories(patchAbsolute.getParent());
                try (FileWriter fw = new FileWriter(patchAbsolute.toFile())) {
                    // strip and write to the file
                    for (final String str : header) {
                        fw.write("# " + str + "\n");
                    }
                    fw.write("\n");
                }
            } catch (IOException ioe) {
                throw new RuntimeException("Couldn't write patch file", ioe);
            }

            // the patch doesn't exist, so it is guaranteed to be the default
            resolver.onFinishLoad(base);
            return;
        }

        // the patch DOES exist, so we need to check for any options. if no options
        // are defined in the patch, we just return the default. otherwise, we patch the values

        try {
            Node patchNode = YAML.compose(new FileReader(patchAbsolute.toFile()));

            // null or non-mapping means the patch is empty, just return base
            if (patchNode == null) {
                resolver.onFinishLoad(base);
                return;
            }

            if (!(patchNode instanceof MappingNode patchMapping)) {
                throw new UnsupportedOperationException("Patch was not MappingNode, found " + patchNode.getClass().getSimpleName());
            }

            // represent the base object as a node tree so we can merge into it
            Node baseNode = YAML.represent(base);
            baseNode.setTag(Tag.MAP);

            if (!(baseNode instanceof MappingNode baseMapping)) {
                throw new UnsupportedOperationException("Base node was not MappingNode");
            }

            // deep-merge patch onto base node
            mergeNodes(baseMapping, patchMapping, "");

            // serialize the merged node back to a string and load it as C
            StringWriter sw = new StringWriter();
            YAML.serialize(baseNode, sw);

            //noinspection unchecked
            C merged = (C) YAML.loadAs(new StringReader(sw.toString()), defaultSupplier.get().getClass());

            resolver.onFinishLoad(injectNode(merged, baseNode));
        } catch (FileNotFoundException fnfe) {
            throw new RuntimeException("Unable to find patch file", fnfe);
        }
    }
}
