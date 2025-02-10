package io.canvasmc.canvas.dfc.common.gen;

import com.google.common.io.Files;
import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ast.McToAst;
import io.canvasmc.canvas.dfc.common.ast.dfvisitor.StripBlending;
import io.canvasmc.canvas.dfc.common.ast.misc.ConstantNode;
import io.canvasmc.canvas.dfc.common.ast.misc.RootNode;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import io.canvasmc.canvas.dfc.common.vif.AstVanillaInterface;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

public class BytecodeGen {
    private static final File exportDir = new File("./cache/c2me-dfc");
    private static final AtomicLong ordinal = new AtomicLong();
    public static final Hash.Strategy<AstNode> RELAXED_STRATEGY;
    private static final Object2ReferenceMap<AstNode, Class<?>> compilationCache;

    public BytecodeGen() {
    }

    public static DensityFunction compile(DensityFunction densityFunction, Reference2ReferenceMap<DensityFunction, DensityFunction> tempCache) {
        DensityFunction cached = (DensityFunction)tempCache.get(densityFunction);
        if (cached != null) {
            return cached;
        } else if (densityFunction instanceof AstVanillaInterface) {
            AstVanillaInterface vif = (AstVanillaInterface)densityFunction;
            AstNode ast = vif.getAstNode();
            return new CompiledDensityFunction(compile0(ast), vif.getBlendingFallback());
        } else {
            AstNode ast = McToAst.toAst(densityFunction.mapAll(StripBlending.INSTANCE));
            if (ast instanceof ConstantNode) {
                ConstantNode constantNode = (ConstantNode)ast;
                return DensityFunctions.constant(constantNode.getValue());
            } else {
                CompiledDensityFunction compiled = new CompiledDensityFunction(compile0(ast), densityFunction);
                tempCache.put(densityFunction, compiled);
                return compiled;
            }
        }
    }

    public static synchronized CompiledEntry compile0(AstNode node) {
        Class<?> cached = (Class)compilationCache.get(node);
        ClassWriter writer = new ClassWriter(3);
        String name = cached != null ? String.format("DfcCompiled_discarded") : String.format("DfcCompiled_%d", ordinal.getAndIncrement());
        writer.visit(65, 17, name, (String)null, Type.getInternalName(Object.class), new String[]{Type.getInternalName(CompiledEntry.class)});
        RootNode rootNode = new RootNode(node);
        Context genContext = new Context(writer, name);
        genContext.newSingleMethod0((adapter, localVarConsumer) -> {
            rootNode.doBytecodeGenSingle(genContext, adapter, localVarConsumer);
        }, "evalSingle", true);
        genContext.newMultiMethod0((adapter, localVarConsumer) -> {
            rootNode.doBytecodeGenMulti(genContext, adapter, localVarConsumer);
        }, "evalMulti", true);
        List<Object> args = (List)genContext.args.entrySet().stream().sorted(Comparator.comparingInt((o) -> {
            return ((Context.FieldRecord)o.getValue()).ordinal();
        })).map(Map.Entry::getKey).collect(Collectors.toCollection(ArrayList::new));
        if (cached != null) {
            try {
                return (CompiledEntry)cached.getConstructor(List.class).newInstance(args);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var11) {
                ReflectiveOperationException e = var11;
                throw new RuntimeException(e);
            }
        } else {
            genConstructor(genContext);
            genGetArgs(genContext);
            genNewInstance(genContext);

            Object var8;
            for(ListIterator<Object> iterator = args.listIterator(); iterator.hasNext(); var8 = iterator.next()) {
            }

            byte[] bytes = writer.toByteArray();
            dumpClass(genContext.className, bytes);
            Class<?> defined = defineClass(genContext.className, bytes);
            compilationCache.put(node, defined);

            try {
                return (CompiledEntry)defined.getConstructor(List.class).newInstance(args);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var12) {
                ReflectiveOperationException e = var12;
                throw new RuntimeException(e);
            }
        }
    }

    private static void genConstructor(Context context) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(context.className, 1, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(List.class)}), context.classWriter.visitMethod(1, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(List.class)}), (String)null, (String[])null)));
        Label start = new Label();
        Label end = new Label();
        m.visitLabel(start);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(Type.getInternalName(Object.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[0]), false);
        Iterator var4 = context.args.entrySet().stream().sorted(Comparator.comparingInt((o) -> {
            return ((Context.FieldRecord)o.getValue()).ordinal();
        })).toList().iterator();

        while(var4.hasNext()) {
            Map.Entry<Object, Context.FieldRecord> entry = (Map.Entry)var4.next();
            String name = ((Context.FieldRecord)entry.getValue()).name();
            Class<?> type = ((Context.FieldRecord)entry.getValue()).type();
            int ordinal = ((Context.FieldRecord)entry.getValue()).ordinal();
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.iconst(ordinal);
            m.invokeinterface(Type.getInternalName(List.class), "get", Type.getMethodDescriptor(InstructionAdapter.OBJECT_TYPE, new Type[]{Type.INT_TYPE}));
            m.checkcast(Type.getType(type));
            m.putfield(context.className, name, Type.getDescriptor(type));
        }

        var4 = context.postProcessMethods.stream().sorted().toList().iterator();

        while(var4.hasNext()) {
            String postProcessingMethod = (String)var4.next();
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(context.className, postProcessingMethod, "()V", false);
        }

        m.areturn(Type.VOID_TYPE);
        m.visitLabel(end);
        m.visitLocalVariable("this", context.classDesc, (String)null, start, end, 0);
        m.visitLocalVariable("list", Type.getDescriptor(List.class), (String)null, start, end, 1);
        m.visitMaxs(0, 0);
    }

    private static void genGetArgs(Context context) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(context.className, 17, "getArgs", Type.getMethodDescriptor(Type.getType(List.class), new Type[0]), context.classWriter.visitMethod(17, "getArgs", Type.getMethodDescriptor(Type.getType(List.class), new Type[0]), (String)null, (String[])null)));
        Label start = new Label();
        Label end = new Label();
        m.visitLabel(start);
        m.anew(Type.getType(ArrayList.class));
        m.dup();
        m.iconst(context.args.size());
        m.invokespecial(Type.getInternalName(ArrayList.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.INT_TYPE}), false);
        m.store(1, InstructionAdapter.OBJECT_TYPE);
        Iterator var4 = context.args.entrySet().stream().sorted(Comparator.comparingInt((o) -> {
            return ((Context.FieldRecord)o.getValue()).ordinal();
        })).toList().iterator();

        while(var4.hasNext()) {
            Map.Entry<Object, Context.FieldRecord> entry = (Map.Entry)var4.next();
            String name = ((Context.FieldRecord)entry.getValue()).name();
            Class<?> type = ((Context.FieldRecord)entry.getValue()).type();
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, name, Type.getDescriptor(type));
            m.invokeinterface(Type.getInternalName(List.class), "add", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, new Type[]{InstructionAdapter.OBJECT_TYPE}));
            m.pop();
        }

        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.areturn(InstructionAdapter.OBJECT_TYPE);
        m.visitLabel(end);
        m.visitLocalVariable("this", context.classDesc, (String)null, start, end, 0);
        m.visitLocalVariable("list", Type.getDescriptor(List.class), (String)null, start, end, 1);
        m.visitMaxs(0, 0);
    }

    private static void genNewInstance(Context context) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(context.className, 17, "newInstance", Type.getMethodDescriptor(Type.getType(CompiledEntry.class), new Type[]{Type.getType(List.class)}), context.classWriter.visitMethod(17, "newInstance", Type.getMethodDescriptor(Type.getType(CompiledEntry.class), new Type[]{Type.getType(List.class)}), (String)null, (String[])null)));
        Label start = new Label();
        Label end = new Label();
        m.visitLabel(start);
        m.anew(Type.getType(context.classDesc));
        m.dup();
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(context.className, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(List.class)}), false);
        m.areturn(InstructionAdapter.OBJECT_TYPE);
        m.visitLabel(end);
        m.visitLocalVariable("this", context.classDesc, (String)null, start, end, 0);
        m.visitLocalVariable("list", Type.getDescriptor(List.class), (String)null, start, end, 1);
        m.visitMaxs(0, 0);
    }

    private static void dumpClass(String className, byte[] bytes) {
        File outputFile = new File(exportDir, className + ".class");
        outputFile.getParentFile().mkdirs();

        try {
            Files.write(bytes, outputFile);
        } catch (IOException var4) {
            IOException e = var4;
            e.printStackTrace();
        }

    }

    private static Class<?> defineClass(final String className, final byte[] bytes) {
        ClassLoader classLoader = new ClassLoader(BytecodeGen.class.getClassLoader()) {
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                return name.equals(className) ? super.defineClass(name, bytes, 0, bytes.length) : super.loadClass(name);
            }
        };

        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException var4) {
            ClassNotFoundException e = var4;
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            io.canvasmc.canvas.util.Files.deleteRecursively(exportDir);
        } catch (IOException var1) {
            IOException e = var1;
            e.printStackTrace();
        }

        RELAXED_STRATEGY = new Hash.Strategy<AstNode>() {
            public int hashCode(AstNode o) {
                return o.relaxedHashCode();
            }

            public boolean equals(AstNode a, AstNode b) {
                return a.relaxedEquals(b);
            }
        };
        compilationCache = Object2ReferenceMaps.synchronize(new Object2ReferenceOpenCustomHashMap<>(RELAXED_STRATEGY));
    }

    public static class Context {
        public static final String SINGLE_DESC;
        public static final String MULTI_DESC;
        public final ClassWriter classWriter;
        public final String className;
        public final String classDesc;
        private int methodIdx = 0;
        private final Object2ReferenceOpenHashMap<AstNode, String> singleMethods = new Object2ReferenceOpenHashMap<>();
        private final Object2ReferenceOpenHashMap<AstNode, String> multiMethods = new Object2ReferenceOpenHashMap<>();
        private final Object2ReferenceOpenHashMap<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>, String> splineMethods = new Object2ReferenceOpenHashMap<>();
        private final ObjectOpenHashSet<String> postProcessMethods = new ObjectOpenHashSet<>();
        private final Reference2ObjectOpenHashMap<Object, FieldRecord> args = new Reference2ObjectOpenHashMap<>();

        public Context(ClassWriter classWriter, String className) {
            this.classWriter = (ClassWriter)Objects.requireNonNull(classWriter);
            this.className = (String)Objects.requireNonNull(className);
            this.classDesc = String.format("L%s;", this.className);
        }

        public String nextMethodName() {
            return String.format("method_%d", this.methodIdx++);
        }

        public String nextMethodName(String suffix) {
            return String.format("method_%d_%s", this.methodIdx++, suffix);
        }

        public String newSingleMethod(AstNode node) {
            return this.singleMethods.computeIfAbsent(node, (AstNode node1) -> this.newSingleMethod((adapter, localVarConsumer) -> node1.doBytecodeGenSingle(this, adapter, localVarConsumer), nextMethodName(node.getClass().getSimpleName())));
        }

        public String newSingleMethod(BiConsumer<InstructionAdapter, LocalVarConsumer> generator) {
            return this.newSingleMethod(generator, this.nextMethodName());
        }

        public String newSingleMethod(BiConsumer<InstructionAdapter, LocalVarConsumer> generator, String name) {
            this.newSingleMethod0(generator, name, false);
            return name;
        }

        private void newSingleMethod0(BiConsumer<InstructionAdapter, LocalVarConsumer> generator, String name, boolean isPublic) {
            InstructionAdapter adapter = new InstructionAdapter(new AnalyzerAdapter(this.className, (isPublic ? 1 : 2) | 16, name, SINGLE_DESC, this.classWriter.visitMethod((isPublic ? 1 : 2) | 16, name, SINGLE_DESC, (String)null, (String[])null)));
            List<IntObjectPair<Pair<String, String>>> extraLocals = new ArrayList<>();
            Label start = new Label();
            Label end = new Label();
            adapter.visitLabel(start);
            generator.accept(adapter, (localName, localDesc) -> {
                int ordinal = extraLocals.size() + 5;
                extraLocals.add(IntObjectPair.of(ordinal, Pair.of(localName, localDesc)));
                return ordinal;
            });
            adapter.visitLabel(end);
            adapter.visitLocalVariable("this", this.classDesc, (String)null, start, end, 0);
            adapter.visitLocalVariable("x", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 1);
            adapter.visitLocalVariable("y", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 2);
            adapter.visitLocalVariable("z", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 3);
            adapter.visitLocalVariable("evalType", Type.getType(EvalType.class).getDescriptor(), (String)null, start, end, 4);
            Iterator var8 = extraLocals.iterator();

            while(var8.hasNext()) {
                IntObjectPair<Pair<String, String>> local = (IntObjectPair)var8.next();
                adapter.visitLocalVariable((String)((Pair)local.right()).left(), (String)((Pair)local.right()).right(), (String)null, start, end, local.leftInt());
            }

            adapter.visitMaxs(0, 0);
        }

        public String newMultiMethod(AstNode node) {
            return this.multiMethods.computeIfAbsent(node, (AstNode node1) -> this.newMultiMethod((adapter, localVarConsumer) -> node1.doBytecodeGenMulti(this, adapter, localVarConsumer), nextMethodName(node.getClass().getSimpleName())));
        }

        public String newMultiMethod(BiConsumer<InstructionAdapter, LocalVarConsumer> generator) {
            return this.newMultiMethod(generator, this.nextMethodName());
        }

        public String newMultiMethod(BiConsumer<InstructionAdapter, LocalVarConsumer> generator, String name) {
            this.newMultiMethod0(generator, name, false);
            return name;
        }

        private void newMultiMethod0(BiConsumer<InstructionAdapter, LocalVarConsumer> generator, String name, boolean isPublic) {
            InstructionAdapter adapter = new InstructionAdapter(new AnalyzerAdapter(this.className, (isPublic ? 1 : 2) | 16, name, MULTI_DESC, this.classWriter.visitMethod((isPublic ? 1 : 2) | 16, name, MULTI_DESC, (String)null, (String[])null)));
            List<IntObjectPair<Pair<String, String>>> extraLocals = new ArrayList();
            Label start = new Label();
            Label end = new Label();
            adapter.visitLabel(start);
            generator.accept(adapter, (localName, localDesc) -> {
                int ordinal = extraLocals.size() + 7;
                extraLocals.add(IntObjectPair.of(ordinal, Pair.of(localName, localDesc)));
                return ordinal;
            });
            adapter.visitLabel(end);
            adapter.visitLocalVariable("this", this.classDesc, (String)null, start, end, 0);
            adapter.visitLocalVariable("res", Type.getType(double[].class).getDescriptor(), (String)null, start, end, 1);
            adapter.visitLocalVariable("x", Type.getType(double[].class).getDescriptor(), (String)null, start, end, 2);
            adapter.visitLocalVariable("y", Type.getType(double[].class).getDescriptor(), (String)null, start, end, 3);
            adapter.visitLocalVariable("z", Type.getType(double[].class).getDescriptor(), (String)null, start, end, 4);
            adapter.visitLocalVariable("evalType", Type.getType(EvalType.class).getDescriptor(), (String)null, start, end, 5);
            adapter.visitLocalVariable("arrayCache", Type.getType(ArrayCache.class).getDescriptor(), (String)null, start, end, 6);
            Iterator var8 = extraLocals.iterator();

            while(var8.hasNext()) {
                IntObjectPair<Pair<String, String>> local = (IntObjectPair)var8.next();
                adapter.visitLocalVariable((String)((Pair)local.right()).left(), (String)((Pair)local.right()).right(), (String)null, start, end, local.leftInt());
            }

            adapter.visitMaxs(0, 0);
        }

        public String getCachedSplineMethod(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
            return (String)this.splineMethods.get(spline);
        }

        public void cacheSplineMethod(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, String method) {
            this.splineMethods.put(spline, method);
        }

        public void callDelegateSingle(InstructionAdapter m, String target) {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(1, Type.INT_TYPE);
            m.load(2, Type.INT_TYPE);
            m.load(3, Type.INT_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(this.className, target, SINGLE_DESC, false);
        }

        public void callDelegateMulti(InstructionAdapter m, String target) {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(5, InstructionAdapter.OBJECT_TYPE);
            m.load(6, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(this.className, target, MULTI_DESC, false);
        }

        public <T> String newField(Class<T> type, T data) {
            FieldRecord existing = (FieldRecord)this.args.get(data);
            if (existing != null) {
                return existing.name();
            } else {
                int size = this.args.size();
                String name = String.format("field_%d", size);
                this.classWriter.visitField(2, name, Type.getDescriptor(type), (String)null, (Object)null);
                this.args.put(data, new FieldRecord(name, size, type));
                return name;
            }
        }

        public void doCountedLoop(InstructionAdapter m, LocalVarConsumer localVarConsumer, IntConsumer bodyGenerator) {
            int loopIdx = localVarConsumer.createLocalVariable("loopIdx", Type.INT_TYPE.getDescriptor());
            m.iconst(0);
            m.store(loopIdx, Type.INT_TYPE);
            Label start = new Label();
            Label end = new Label();
            m.visitLabel(start);
            m.load(loopIdx, Type.INT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.arraylength();
            m.ificmpge(end);
            bodyGenerator.accept(loopIdx);
            m.iinc(loopIdx, 1);
            m.goTo(start);
            m.visitLabel(end);
        }

        public void delegateToSingle(InstructionAdapter m, LocalVarConsumer localVarConsumer, AstNode current) {
            String singleMethod = this.newSingleMethod(current);
            this.doCountedLoop(m, localVarConsumer, (idx) -> {
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.load(0, InstructionAdapter.OBJECT_TYPE);
                m.load(2, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.INT_TYPE);
                m.load(3, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.INT_TYPE);
                m.load(4, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.INT_TYPE);
                m.load(5, InstructionAdapter.OBJECT_TYPE);
                m.invokevirtual(this.className, singleMethod, SINGLE_DESC, false);
                m.astore(Type.DOUBLE_TYPE);
            });
        }

        public void genPostprocessingMethod(String name, Consumer<InstructionAdapter> generator) {
            if (!this.postProcessMethods.contains(name)) {
                InstructionAdapter adapter = new InstructionAdapter(new AnalyzerAdapter(this.className, 18, name, "()V", this.classWriter.visitMethod(18, name, "()V", (String)null, (String[])null)));
                Label start = new Label();
                Label end = new Label();
                adapter.visitLabel(start);
                generator.accept(adapter);
                adapter.visitLabel(end);
                adapter.visitMaxs(0, 0);
                adapter.visitLocalVariable("this", this.classDesc, (String)null, start, end, 0);
                this.postProcessMethods.add(name);
            }
        }

        static {
            SINGLE_DESC = Type.getMethodDescriptor(Type.getType(Double.TYPE), new Type[]{Type.getType(Integer.TYPE), Type.getType(Integer.TYPE), Type.getType(Integer.TYPE), Type.getType(EvalType.class)});
            MULTI_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(EvalType.class), Type.getType(ArrayCache.class)});
        }

        public interface LocalVarConsumer {
            int createLocalVariable(String var1, String var2);
        }

        private static record FieldRecord(String name, int ordinal, Class<?> type) {
            private FieldRecord(String name, int ordinal, Class<?> type) {
                this.name = name;
                this.ordinal = ordinal;
                this.type = type;
            }

            public String name() {
                return this.name;
            }

            public int ordinal() {
                return this.ordinal;
            }

            public Class<?> type() {
                return this.type;
            }
        }
    }

    @FunctionalInterface
    public interface EvalMultiInterface {
        void evalMulti(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5);
    }

    @FunctionalInterface
    public interface EvalSingleInterface {
        double evalSingle(int var1, int var2, int var3, EvalType var4);
    }
}
