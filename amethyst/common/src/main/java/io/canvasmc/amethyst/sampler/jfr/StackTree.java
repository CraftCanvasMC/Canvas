package io.canvasmc.amethyst.sampler.jfr;

import io.canvasmc.amethyst.AmethystUtils;
import io.canvasmc.amethyst.proto.Frame;
import io.canvasmc.amethyst.proto.Samples;
import io.canvasmc.amethyst.source.SourceTable;
import io.canvasmc.amethyst.table.IndexTable;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import one.jfr.ClassRef;
import one.jfr.JfrReader;
import one.jfr.MethodRef;
import one.jfr.StackTrace;

final class StackTree {

    // async-profiler frame types, the proto Frame.Type is each plus one:
    // - 0 - interpreted
    // - 1 - jit
    // - 2 - inlined
    // - 3 - native
    // - 4 - cpp
    // - 5 - kernel
    // - 6 - c1

    private static final int MAX_FRAME_TYPE = 6;

    // an empty stack is node -1, so a miss is -2
    private static final int MISSING = -2;

    private final SourceTable sources;

    private final IndexTable<String> strings = new IndexTable<>();
    private final IndexTable<FrameKey> frames = new IndexTable<>();
    private final IntArrayList nodeParents = new IntArrayList();
    private final IntArrayList nodeFrames = new IntArrayList();
    private final Long2IntOpenHashMap nodeIndex = new Long2IntOpenHashMap();

    // jfr ids only mean something within one chunk
    private final Int2IntOpenHashMap chunkStacks = new Int2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<Method> chunkMethods = new Long2ObjectOpenHashMap<>();

    private int writtenNodes;

    StackTree(final SourceTable sources) {
        this.sources = sources;

        this.nodeIndex.defaultReturnValue(-1);
        this.chunkStacks.defaultReturnValue(MISSING);
    }

    void newChunk() {
        chunkStacks.clear();
        chunkMethods.clear();
    }

    int stack(final JfrReader reader, final int stackTraceId) {
        final int existing = chunkStacks.get(stackTraceId);
        if (existing != MISSING) {
            return existing;
        }

        final StackTrace trace = reader.stackTraces.get(stackTraceId);
        int node = -1;
        if (trace != null) {
            for (int i = trace.methods.length - 1; i >= 0; i--) {
                final int frame = frame(reader, trace.methods[i], trace.types[i], trace.locations[i] >>> 16);
                final long key = AmethystUtils.packInts(node, frame);
                final int child = nodeIndex.get(key);

                if (child == -1) {
                    nodeParents.add(node);
                    nodeFrames.add(frame);
                    node = nodeParents.size() - 1;
                    nodeIndex.put(key, node);
                }
                else {
                    node = child;
                }
            }
        }

        chunkStacks.put(stackTraceId, node);
        return node;
    }

    int className(final JfrReader reader, final long classId) {
        final ClassRef ref = reader.classes.get(classId);
        return ref == null ? -1 : strings.add(symbol(reader, ref.name).replace('/', '.'));
    }

    private int frame(final JfrReader reader, final long methodId, final byte type, final int line) {
        final Method method = chunkMethods.computeIfAbsent(methodId, (id) -> method(reader, id));
        final int protoType = type >= 0 && type <= MAX_FRAME_TYPE ? type + 1 : Frame.Type.TYPE_UNSPECIFIED_VALUE;
        return frames.add(new FrameKey(method, line, protoType));
    }

    private Method method(final JfrReader reader, final long methodId) {
        final MethodRef ref = reader.methods.get(methodId);
        if (ref == null) {
            return new Method(strings.add(""), strings.add("unknown"), strings.add(""), 0);
        }

        // cpp and kernel frames have no class
        final ClassRef classRef = reader.classes.get(ref.cls);
        final String className = classRef == null ? "" : symbol(reader, classRef.name).replace('/', '.');
        return new Method(
            strings.add(className),
            strings.add(symbol(reader, ref.name)),
            strings.add(symbol(reader, ref.sig)),
            className.isEmpty() ? 0 : sources.of(className)
        );
    }

    void write(final Samples.Builder out) {
        out.addAllStrings(strings.unwritten());
        for (final FrameKey frame : frames.unwritten()) {
            out.addFrames(
                Frame.newBuilder()
                    .setClassName(frame.method.className)
                    .setMethodName(frame.method.name)
                    .setSignature(frame.method.signature)
                    .setLine(frame.line)
                    .setTypeValue(frame.type)
                    .setSource(frame.method.source)
            );
        }

        // parents always come before their children, so the distance is never zero
        for (int i = writtenNodes; i < nodeParents.size(); i++) {
            out.addNodeParentDistance(i - nodeParents.getInt(i)).addNodeFrame(nodeFrames.getInt(i));
        }

        writtenNodes = nodeParents.size();
    }

    private static String symbol(final JfrReader reader, final long id) {
        final byte[] bytes = reader.symbols.get(id);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private record Method(int className, int name, int signature, int source) { }

    private record FrameKey(Method method, int line, int type) { }
}
