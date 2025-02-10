package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import io.canvasmc.canvas.dfc.common.vif.EachApplierVanillaInterface;
import io.canvasmc.canvas.dfc.common.vif.NoisePosVanillaInterface;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class DelegateNode implements AstNode {
    private final DensityFunction densityFunction;

    public DelegateNode(DensityFunction densityFunction) {
        this.densityFunction = Objects.requireNonNull(densityFunction);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.densityFunction.compute(new NoisePosVanillaInterface(x, y, z, type));
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        if (res.length == 1) {
            res[0] = this.evalSingle(x[0], y[0], z[0], type);
        } else {
            this.densityFunction.fillArray(res, new EachApplierVanillaInterface(x, y, z, type));
        }
    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String newField = context.newField(DensityFunction.class, this.densityFunction);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.anew(Type.getType(NoisePosVanillaInterface.class));
        m.dup();
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(Type.getInternalName(NoisePosVanillaInterface.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(EvalType.class)), false);
        m.invokeinterface(Type.getInternalName(DensityFunction.class), "compute", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.FunctionContext.class)));
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String newField = context.newField(DensityFunction.class, this.densityFunction);
        Label moreThanTwoLabel = new Label();
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(1);
        m.ificmpgt(moreThanTwoLabel);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.anew(Type.getType(NoisePosVanillaInterface.class));
        m.dup();
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.iconst(0);
        m.aload(Type.INT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(Type.getInternalName(NoisePosVanillaInterface.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(EvalType.class)), false);
        m.invokeinterface(Type.getInternalName(DensityFunction.class), "compute", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.FunctionContext.class)));
        m.astore(Type.DOUBLE_TYPE);
        m.areturn(Type.VOID_TYPE);
        m.visitLabel(moreThanTwoLabel);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, newField, Type.getDescriptor(DensityFunction.class));
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.anew(Type.getType(EachApplierVanillaInterface.class));
        m.dup();
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokespecial(Type.getInternalName(EachApplierVanillaInterface.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(int[].class), Type.getType(int[].class), Type.getType(int[].class), Type.getType(EvalType.class), Type.getType(ArrayCache.class)), false);
        m.invokeinterface(Type.getInternalName(DensityFunction.class), "fillArray", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class), Type.getType(DensityFunction.ContextProvider.class)));
        m.areturn(Type.VOID_TYPE);
    }

    public DensityFunction getDelegate() {
        return this.densityFunction;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DelegateNode that = (DelegateNode)o;
            return Objects.equals(this.densityFunction, that.densityFunction);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(this.getClass());
        result = 31 * result + Objects.hashCode(this.densityFunction);
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DelegateNode that = (DelegateNode)o;
            return this.densityFunction.getClass() == that.densityFunction.getClass();
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(this.getClass());
        result = 31 * result + Objects.hashCode(this.densityFunction.getClass());
        return result;
    }
}
