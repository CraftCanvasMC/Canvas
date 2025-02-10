package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ducks.IFastCacheLike;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen.Context;
import io.canvasmc.canvas.dfc.common.gen.IMultiMethod;
import io.canvasmc.canvas.dfc.common.gen.ISingleMethod;
import io.canvasmc.canvas.dfc.common.gen.SubCompiledDensityFunction;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class CacheLikeNode implements AstNode {
    private final IFastCacheLike cacheLike;
    private final AstNode delegate;

    public CacheLikeNode(IFastCacheLike cacheLike, AstNode delegate) {
        this.cacheLike = cacheLike;
        this.delegate = (AstNode)Objects.requireNonNull(delegate);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        if (this.cacheLike == null) {
            return this.delegate.evalSingle(x, y, z, type);
        } else {
            double cached = this.cacheLike.c2me$getCached(x, y, z, type);
            if (Double.doubleToRawLongBits(cached) != 9222769054270909007L) {
                return cached;
            } else {
                double eval = this.delegate.evalSingle(x, y, z, type);
                this.cacheLike.c2me$cache(x, y, z, type, eval);
                return eval;
            }
        }
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        if (this.cacheLike == null) {
            this.delegate.evalMulti(res, x, y, z, type);
        } else {
            boolean cached = this.cacheLike.c2me$getCached(res, x, y, z, type);
            if (!cached) {
                this.delegate.evalMulti(res, x, y, z, type);
                this.cacheLike.c2me$cache(res, x, y, z, type);
            }

        }
    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.delegate};
    }

    public AstNode transform(AstTransformer transformer) {
        AstNode delegate = this.delegate.transform(transformer);
        return this.delegate == delegate ? transformer.transform(this) : transformer.transform(new CacheLikeNode(this.cacheLike, delegate));
    }

    public void doBytecodeGenSingle(@NotNull Context context, @NotNull InstructionAdapter m, Context.@NotNull LocalVarConsumer localVarConsumer) {
        String delegateMethod = context.newSingleMethod(this.delegate);
        String cacheLikeField = context.newField(IFastCacheLike.class, this.cacheLike);
        this.genPostprocessingMethod(context, cacheLikeField);
        int eval = localVarConsumer.createLocalVariable("eval", Type.DOUBLE_TYPE.getDescriptor());
        Label cacheExists = new Label();
        Label cacheMiss = new Label();
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.ifnonnull(cacheExists);
        context.callDelegateSingle(m, delegateMethod);
        m.areturn(Type.DOUBLE_TYPE);
        m.visitLabel(cacheExists);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(IFastCacheLike.class), "c2me$getCached", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(EvalType.class)}));
        m.dup2();
        m.invokestatic(Type.getInternalName(Double.class), "doubleToRawLongBits", Type.getMethodDescriptor(Type.LONG_TYPE, new Type[]{Type.DOUBLE_TYPE}), false);
        m.lconst(9222769054270909007L);
        m.lcmp();
        m.ifeq(cacheMiss);
        m.areturn(Type.DOUBLE_TYPE);
        m.visitLabel(cacheMiss);
        m.pop2();
        context.callDelegateSingle(m, delegateMethod);
        m.store(eval, Type.DOUBLE_TYPE);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(eval, Type.DOUBLE_TYPE);
        m.invokeinterface(Type.getInternalName(IFastCacheLike.class), "c2me$cache", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(EvalType.class), Type.DOUBLE_TYPE}));
        m.load(eval, Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(@NotNull Context context, @NotNull InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String delegateMethod = context.newMultiMethod(this.delegate);
        String cacheLikeField = context.newField(IFastCacheLike.class, this.cacheLike);
        this.genPostprocessingMethod(context, cacheLikeField);
        Label cacheExists = new Label();
        Label cacheMiss = new Label();
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.ifnonnull(cacheExists);
        context.callDelegateMulti(m, delegateMethod);
        m.areturn(Type.VOID_TYPE);
        m.visitLabel(cacheExists);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(IFastCacheLike.class), "c2me$getCached", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, new Type[]{Type.getType(double[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(EvalType.class)}));
        m.ifeq(cacheMiss);
        m.areturn(Type.VOID_TYPE);
        m.visitLabel(cacheMiss);
        context.callDelegateMulti(m, delegateMethod);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(IFastCacheLike.class), "c2me$cache", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(EvalType.class)}));
        m.areturn(Type.VOID_TYPE);
    }

    private void genPostprocessingMethod(@NotNull Context context, String cacheLikeField) {
        String methodName = String.format("postProcessing_%s", cacheLikeField);
        String delegateSingle = context.newSingleMethod(this.delegate);
        String delegateMulti = context.newMultiMethod(this.delegate);
        context.genPostprocessingMethod(methodName, (m) -> {
            Label cacheExists = new Label();
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
            m.dup();
            m.ifnonnull(cacheExists);
            m.pop();
            m.pop();
            m.areturn(Type.VOID_TYPE);
            m.visitLabel(cacheExists);
            m.anew(Type.getType(SubCompiledDensityFunction.class));
            m.dup();
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.invokedynamic("evalSingle", Type.getMethodDescriptor(Type.getType(ISingleMethod.class), new Type[]{Type.getType(context.classDesc)}), new Handle(6, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), new Object[]{Type.getMethodType(Context.SINGLE_DESC), new Handle(5, context.className, delegateSingle, Context.SINGLE_DESC, false), Type.getMethodType(Context.SINGLE_DESC)});
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.invokedynamic("evalMulti", Type.getMethodDescriptor(Type.getType(IMultiMethod.class), new Type[]{Type.getType(context.classDesc)}), new Handle(6, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), new Object[]{Type.getMethodType(Context.MULTI_DESC), new Handle(5, context.className, delegateMulti, Context.MULTI_DESC, false), Type.getMethodType(Context.MULTI_DESC)});
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
            m.checkcast(Type.getType(DensityFunction.class));
            m.invokespecial(Type.getInternalName(SubCompiledDensityFunction.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(ISingleMethod.class), Type.getType(IMultiMethod.class), Type.getType(DensityFunction.class)}), false);
            m.checkcast(Type.getType(DensityFunction.class));
            m.invokeinterface(Type.getInternalName(IFastCacheLike.class), "c2me$withDelegate", Type.getMethodDescriptor(Type.getType(DensityFunction.class), new Type[]{Type.getType(DensityFunction.class)}));
            m.putfield(context.className, cacheLikeField, Type.getDescriptor(IFastCacheLike.class));
            m.areturn(Type.VOID_TYPE);
        });
    }

    public IFastCacheLike getCacheLike() {
        return this.cacheLike;
    }

    public AstNode getDelegate() {
        return this.delegate;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            CacheLikeNode that = (CacheLikeNode)o;
            return equals(this.cacheLike, that.cacheLike) && Objects.equals(this.delegate, that.delegate);
        } else {
            return false;
        }
    }

    private static boolean equals(IFastCacheLike a, IFastCacheLike b) {
        if (a instanceof DensityFunctions.Marker wrappingA) {
            if (b instanceof DensityFunctions.Marker wrappingB) {
                return wrappingA.type() == wrappingB.type();
            }
        }

        return a.equals(b);
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + hashCode(this.cacheLike);
        result = 31 * result + this.delegate.hashCode();
        return result;
    }

    private static int hashCode(IFastCacheLike o) {
        if (o instanceof DensityFunctions.Marker wrapping) {
            return wrapping.type().hashCode();
        } else {
            return o.hashCode();
        }
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            CacheLikeNode that = (CacheLikeNode)o;
            return relaxedEquals(this.cacheLike, that.cacheLike) && this.delegate.relaxedEquals(that.delegate);
        } else {
            return false;
        }
    }

    private static boolean relaxedEquals(IFastCacheLike a, IFastCacheLike b) {
        if (a instanceof DensityFunctions.Marker wrappingA) {
            if (b instanceof DensityFunctions.Marker wrappingB) {
                return wrappingA.type() == wrappingB.type();
            }
        }

        return a.getClass() == b.getClass();
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + relaxedHashCode(this.cacheLike);
        result = 31 * result + this.delegate.relaxedHashCode();
        return result;
    }

    private static int relaxedHashCode(IFastCacheLike o) {
        if (o instanceof DensityFunctions.Marker wrapping) {
            return wrapping.type().hashCode();
        } else {
            return o.getClass().hashCode();
        }
    }
}
