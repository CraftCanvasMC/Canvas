package io.canvasmc.canvas.dfc.common.ast.spline;

import com.ishland.flowsched.util.Assertions;
import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ast.McToAst;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import io.canvasmc.canvas.dfc.common.vif.NoisePosVanillaInterface;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

public class SplineAstNode implements AstNode {
    public static final String SPLINE_METHOD_DESC;
    private final CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline;

    public SplineAstNode(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        this.spline = spline;
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return (double)this.spline.apply(new DensityFunctions.Spline.Point(new NoisePosVanillaInterface(x, y, z, type)));
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for(int i = 0; i < res.length; ++i) {
            res[i] = this.evalSingle(x[i], y[i], z[i], type);
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        ValuesMethodDef splineMethod = doBytecodeGenSpline(context, this.spline);
        callSplineSingle(context, m, splineMethod);
        m.cast(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    private static ValuesMethodDef doBytecodeGenSpline(BytecodeGen.Context context, CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        String name = context.getCachedSplineMethod(spline);
        if (name != null) {
            return new ValuesMethodDef(false, name, 0.0F);
        } else if (spline instanceof CubicSpline.Constant) {
            CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline1 = (CubicSpline.Constant)spline;
            return new ValuesMethodDef(true, (String)null, spline1.value());
        } else {
            name = context.nextMethodName("Spline");
            InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(context.className, 18, name, SPLINE_METHOD_DESC, context.classWriter.visitMethod(18, name, SPLINE_METHOD_DESC, (String)null, (String[])null)));
            List<IntObjectPair<Pair<String, String>>> extraLocals = new ArrayList();
            Label start = new Label();
            Label end = new Label();
            m.visitLabel(start);
            BytecodeGen.Context.LocalVarConsumer localVarConsumer = (localName, localDesc) -> {
                int ordinal = extraLocals.size() + 5;
                extraLocals.add(IntObjectPair.of(ordinal, Pair.of(localName, localDesc)));
                return ordinal;
            };
            if (spline instanceof CubicSpline.Multipoint) {
                CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> impl = (CubicSpline.Multipoint)spline;
                ValuesMethodDef[] valuesMethods = (ValuesMethodDef[])impl.values().stream().map((spline1x) -> {
                    return doBytecodeGenSpline(context, spline1x);
                }).toArray((x$0) -> {
                    return new ValuesMethodDef[x$0];
                });
                String locations = context.newField(float[].class, impl.locations());
                String derivatives = context.newField(float[].class, impl.derivatives());
                int point = localVarConsumer.createLocalVariable("point", Type.FLOAT_TYPE.getDescriptor());
                int rangeForLocation = localVarConsumer.createLocalVariable("rangeForLocation", Type.INT_TYPE.getDescriptor());
                int lastConst = impl.locations().length - 1;
                String locationFunction = context.newSingleMethod(McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)impl.coordinate()).function().value()));
                context.callDelegateSingle(m, locationFunction);
                m.cast(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
                m.store(point, Type.FLOAT_TYPE);
                if (valuesMethods.length == 1) {
                    m.load(point, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    callSplineSingle(context, m, valuesMethods[0]);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
                    m.iconst(0);
                    m.invokestatic(Type.getInternalName(SplineSupport.class), "sampleOutsideRange", Type.getMethodDescriptor(Type.FLOAT_TYPE, new Type[]{Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE}), false);
                    m.areturn(Type.FLOAT_TYPE);
                } else {
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    m.load(point, Type.FLOAT_TYPE);
                    m.invokestatic(Type.getInternalName(SplineSupport.class), "findRangeForLocation", Type.getMethodDescriptor(Type.INT_TYPE, new Type[]{Type.getType(float[].class), Type.FLOAT_TYPE}), false);
                    m.store(rangeForLocation, Type.INT_TYPE);
                    Label label1 = new Label();
                    Label label2 = new Label();
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.ifge(label1);
                    m.load(point, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    callSplineSingle(context, m, valuesMethods[0]);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
                    m.iconst(0);
                    m.invokestatic(Type.getInternalName(SplineSupport.class), "sampleOutsideRange", Type.getMethodDescriptor(Type.FLOAT_TYPE, new Type[]{Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE}), false);
                    m.areturn(Type.FLOAT_TYPE);
                    m.visitLabel(label1);
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.iconst(lastConst);
                    m.ificmpne(label2);
                    m.load(point, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    callSplineSingle(context, m, valuesMethods[lastConst]);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
                    m.iconst(lastConst);
                    m.invokestatic(Type.getInternalName(SplineSupport.class), "sampleOutsideRange", Type.getMethodDescriptor(Type.FLOAT_TYPE, new Type[]{Type.FLOAT_TYPE, Type.getType(float[].class), Type.FLOAT_TYPE, Type.getType(float[].class), Type.INT_TYPE}), false);
                    m.areturn(Type.FLOAT_TYPE);
                    m.visitLabel(label2);
                    int loc0 = localVarConsumer.createLocalVariable("loc0", Type.FLOAT_TYPE.getDescriptor());
                    int loc1 = localVarConsumer.createLocalVariable("loc1", Type.FLOAT_TYPE.getDescriptor());
                    int locDist = localVarConsumer.createLocalVariable("locDist", Type.FLOAT_TYPE.getDescriptor());
                    int k = localVarConsumer.createLocalVariable("k", Type.FLOAT_TYPE.getDescriptor());
                    int n = localVarConsumer.createLocalVariable("n", Type.FLOAT_TYPE.getDescriptor());
                    int o = localVarConsumer.createLocalVariable("o", Type.FLOAT_TYPE.getDescriptor());
                    int onDist = localVarConsumer.createLocalVariable("onDist", Type.FLOAT_TYPE.getDescriptor());
                    int p = localVarConsumer.createLocalVariable("p", Type.FLOAT_TYPE.getDescriptor());
                    int q = localVarConsumer.createLocalVariable("q", Type.FLOAT_TYPE.getDescriptor());
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.aload(Type.FLOAT_TYPE);
                    m.store(loc0, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, locations, Type.getDescriptor(float[].class));
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.iconst(1);
                    m.add(Type.INT_TYPE);
                    m.aload(Type.FLOAT_TYPE);
                    m.store(loc1, Type.FLOAT_TYPE);
                    m.load(loc1, Type.FLOAT_TYPE);
                    m.load(loc0, Type.FLOAT_TYPE);
                    m.sub(Type.FLOAT_TYPE);
                    m.store(locDist, Type.FLOAT_TYPE);
                    m.load(point, Type.FLOAT_TYPE);
                    m.load(loc0, Type.FLOAT_TYPE);
                    m.sub(Type.FLOAT_TYPE);
                    m.load(locDist, Type.FLOAT_TYPE);
                    m.div(Type.FLOAT_TYPE);
                    m.store(k, Type.FLOAT_TYPE);
                    Label[] jumpLabels = new Label[valuesMethods.length - 1];
                    boolean[] jumpGenerated = new boolean[valuesMethods.length - 1];

                    for(int i = 0; i < valuesMethods.length - 1; ++i) {
                        jumpLabels[i] = new Label();
                    }

                    Label defaultLabel = new Label();
                    Label label3 = new Label();
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.tableswitch(0, valuesMethods.length - 2, defaultLabel, jumpLabels);

                    for(int i = 0; i < valuesMethods.length - 1; ++i) {
                        if (!jumpGenerated[i]) {
                            m.visitLabel(jumpLabels[i]);
                            jumpGenerated[i] = true;

                            for(int j = i + 1; j < valuesMethods.length - 1; ++j) {
                                if (valuesMethods[i].equals(valuesMethods[j]) && valuesMethods[i + 1].equals(valuesMethods[j + 1])) {
                                    m.visitLabel(jumpLabels[j]);
                                    jumpGenerated[j] = true;
                                }
                            }

                            callSplineSingle(context, m, valuesMethods[i]);
                            if (valuesMethods[i].equals(valuesMethods[i + 1])) {
                                m.dup();
                                m.store(n, Type.FLOAT_TYPE);
                                m.store(o, Type.FLOAT_TYPE);
                            } else {
                                m.store(n, Type.FLOAT_TYPE);
                                callSplineSingle(context, m, valuesMethods[i + 1]);
                                m.store(o, Type.FLOAT_TYPE);
                            }

                            m.goTo(label3);
                        }
                    }

                    m.visitLabel(defaultLabel);
                    m.iconst(0);
                    m.aconst("boom");
                    m.invokestatic(Type.getInternalName(Assertions.class), "assertTrue", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.BOOLEAN_TYPE, Type.getType(String.class)}), false);
                    m.fconst(Float.NaN);
                    m.areturn(Type.FLOAT_TYPE);
                    m.visitLabel(label3);
                    m.load(o, Type.FLOAT_TYPE);
                    m.load(n, Type.FLOAT_TYPE);
                    m.sub(Type.FLOAT_TYPE);
                    m.store(onDist, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.aload(Type.FLOAT_TYPE);
                    m.load(locDist, Type.FLOAT_TYPE);
                    m.mul(Type.FLOAT_TYPE);
                    m.load(onDist, Type.FLOAT_TYPE);
                    m.sub(Type.FLOAT_TYPE);
                    m.store(p, Type.FLOAT_TYPE);
                    m.load(0, InstructionAdapter.OBJECT_TYPE);
                    m.getfield(context.className, derivatives, Type.getDescriptor(float[].class));
                    m.load(rangeForLocation, Type.INT_TYPE);
                    m.iconst(1);
                    m.add(Type.INT_TYPE);
                    m.aload(Type.FLOAT_TYPE);
                    m.neg(Type.FLOAT_TYPE);
                    m.load(locDist, Type.FLOAT_TYPE);
                    m.mul(Type.FLOAT_TYPE);
                    m.load(onDist, Type.FLOAT_TYPE);
                    m.add(Type.FLOAT_TYPE);
                    m.store(q, Type.FLOAT_TYPE);
                    m.load(k, Type.FLOAT_TYPE);
                    m.load(n, Type.FLOAT_TYPE);
                    m.load(o, Type.FLOAT_TYPE);
                    m.invokestatic(Type.getInternalName(Mth.class), "lerp", "(FFF)F", false);
                    m.load(k, Type.FLOAT_TYPE);
                    m.fconst(1.0F);
                    m.load(k, Type.FLOAT_TYPE);
                    m.sub(Type.FLOAT_TYPE);
                    m.mul(Type.FLOAT_TYPE);
                    m.load(k, Type.FLOAT_TYPE);
                    m.load(p, Type.FLOAT_TYPE);
                    m.load(q, Type.FLOAT_TYPE);
                    m.invokestatic(Type.getInternalName(Mth.class), "lerp", "(FFF)F", false);
                    m.mul(Type.FLOAT_TYPE);
                    m.add(Type.FLOAT_TYPE);
                    m.areturn(Type.FLOAT_TYPE);
                }
            } else {
                if (!(spline instanceof CubicSpline.Constant)) {
                    throw new UnsupportedOperationException(String.format("Unsupported spline implementation: %s", spline.getClass().getName()));
                }

                CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> floatFunction = (CubicSpline.Constant)spline;
                m.fconst(floatFunction.value());
                m.areturn(Type.FLOAT_TYPE);
            }

            m.visitLabel(end);
            m.visitLocalVariable("this", context.classDesc, (String)null, start, end, 0);
            m.visitLocalVariable("x", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 1);
            m.visitLocalVariable("y", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 2);
            m.visitLocalVariable("z", Type.INT_TYPE.getDescriptor(), (String)null, start, end, 3);
            m.visitLocalVariable("evalType", Type.getType(EvalType.class).getDescriptor(), (String)null, start, end, 4);
            Iterator var35 = extraLocals.iterator();

            while(var35.hasNext()) {
                IntObjectPair<Pair<String, String>> local = (IntObjectPair)var35.next();
                m.visitLocalVariable((String)((Pair)local.right()).left(), (String)((Pair)local.right()).right(), (String)null, start, end, local.leftInt());
            }

            m.visitMaxs(0, 0);
            context.cacheSplineMethod(spline, name);
            return new ValuesMethodDef(false, name, 0.0F);
        }
    }

    private static void callSplineSingle(BytecodeGen.Context context, InstructionAdapter m, ValuesMethodDef target) {
        if (target.isConst()) {
            m.fconst(target.constValue());
        } else {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(1, Type.INT_TYPE);
            m.load(2, Type.INT_TYPE);
            m.load(3, Type.INT_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(context.className, target.generatedMethod(), SPLINE_METHOD_DESC, false);
        }

    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.delegateToSingle(m, localVarConsumer, this);
        m.areturn(Type.VOID_TYPE);
    }

    private static boolean deepEquals(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a, CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            if (b instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b1) {
                return a1.value() == b1.value();
            }
        }

        if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            if (b instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b1) {
                boolean equals1 = Arrays.equals(a1.derivatives(), b1.derivatives()) && Arrays.equals(a1.locations(), b1.locations()) && a1.values().size() == b1.values().size() && McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)a1.coordinate()).function().value()).equals(McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)b1.coordinate()).function().value()));
                if (!equals1) {
                    return false;
                }

                int size = a1.values().size();

                for(int i = 0; i < size; ++i) {
                    if (!deepEquals((CubicSpline)a1.values().get(i), (CubicSpline)b1.values().get(i))) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static boolean deepRelaxedEquals(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a, CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            if (b instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b1) {
                return a1.value() == b1.value();
            }
        }

        if (a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            if (b instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> b1) {
                boolean equals1 = a1.values().size() == b1.values().size() && McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)a1.coordinate()).function().value()).relaxedEquals(McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)b1.coordinate()).function().value()));
                if (!equals1) {
                    return false;
                }

                int size = a1.values().size();

                for(int i = 0; i < size; ++i) {
                    if (!deepRelaxedEquals((CubicSpline)a1.values().get(i), (CubicSpline)b1.values().get(i))) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static int deepHashcode(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            return Float.hashCode(a1.value());
        } else if (!(a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1)) {
            return a.hashCode();
        } else {
            int result = 1;
            result = 31 * result + Arrays.hashCode(a1.derivatives());
            result = 31 * result + Arrays.hashCode(a1.locations());

            CubicSpline spline;
            for(Iterator var4 = a1.values().iterator(); var4.hasNext(); result = 31 * result + deepHashcode(spline)) {
                spline = (CubicSpline)var4.next();
            }

            result = 31 * result + McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)a1.coordinate()).function().value()).hashCode();
            return result;
        }
    }

    private static int deepRelaxedHashcode(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a) {
        if (a instanceof CubicSpline.Constant<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1) {
            return Float.hashCode(a1.value());
        } else if (!(a instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> a1)) {
            return a.hashCode();
        } else {
            int result = 1;

            CubicSpline spline;
            for(Iterator var4 = a1.values().iterator(); var4.hasNext(); result = 31 * result + deepRelaxedHashcode(spline)) {
                spline = (CubicSpline)var4.next();
            }

            result = 31 * result + McToAst.toAst((DensityFunction)((DensityFunctions.Spline.Coordinate)a1.coordinate()).function().value()).relaxedHashCode();
            return result;
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            SplineAstNode that = (SplineAstNode)o;
            return deepEquals(this.spline, that.spline);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return deepHashcode(this.spline);
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            SplineAstNode that = (SplineAstNode)o;
            return deepRelaxedEquals(this.spline, that.spline);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        return deepRelaxedHashcode(this.spline);
    }

    static {
        SPLINE_METHOD_DESC = Type.getMethodDescriptor(Type.getType(Float.TYPE), new Type[]{Type.getType(Integer.TYPE), Type.getType(Integer.TYPE), Type.getType(Integer.TYPE), Type.getType(EvalType.class)});
    }

    private static record ValuesMethodDef(boolean isConst, String generatedMethod, float constValue) {
        private ValuesMethodDef(boolean isConst, String generatedMethod, float constValue) {
            this.isConst = isConst;
            this.generatedMethod = generatedMethod;
            this.constValue = constValue;
        }

        public boolean isConst() {
            return this.isConst;
        }

        public String generatedMethod() {
            return this.generatedMethod;
        }

        public float constValue() {
            return this.constValue;
        }
    }
}
