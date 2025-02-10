package io.canvasmc.canvas.dfc.common.ast.noise;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class DFTShiftNode implements AstNode {
    private final DensityFunction.NoiseHolder offsetNoise;

    public DFTShiftNode(DensityFunction.NoiseHolder offsetNoise) {
        this.offsetNoise = (DensityFunction.NoiseHolder)Objects.requireNonNull(offsetNoise);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.offsetNoise.getValue((double)x * 0.25, (double)y * 0.25, (double)z * 0.25) * 4.0;
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for(int i = 0; i < res.length; ++i) {
            res[i] = this.offsetNoise.getValue((double)x[i] * 0.25, (double)y[i] * 0.25, (double)z[i] * 0.25) * 4.0;
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.offsetNoise);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        m.load(1, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(0.25);
        m.mul(Type.DOUBLE_TYPE);
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(0.25);
        m.mul(Type.DOUBLE_TYPE);
        m.load(3, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(0.25);
        m.mul(Type.DOUBLE_TYPE);
        m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
        m.dconst(4.0);
        m.mul(Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.offsetNoise);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(0.25);
            m.mul(Type.DOUBLE_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(0.25);
            m.mul(Type.DOUBLE_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(0.25);
            m.mul(Type.DOUBLE_TYPE);
            m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
            m.dconst(4.0);
            m.mul(Type.DOUBLE_TYPE);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DFTShiftNode that = (DFTShiftNode)o;
            return Objects.equals(this.offsetNoise, that.offsetNoise);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        Object o = this.getClass();
        result = 31 * result + o.hashCode();
        result = 31 * result + this.offsetNoise.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else {
            return o != null && this.getClass() == o.getClass();
        }
    }

    public int relaxedHashCode() {
        return this.getClass().hashCode();
    }
}
