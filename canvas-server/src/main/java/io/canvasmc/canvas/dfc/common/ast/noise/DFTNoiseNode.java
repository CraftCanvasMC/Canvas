package io.canvasmc.canvas.dfc.common.ast.noise;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class DFTNoiseNode implements AstNode {
    private final DensityFunction.NoiseHolder noise;
    private final double xzScale;
    private final double yScale;

    public DFTNoiseNode(DensityFunction.NoiseHolder noise, double xzScale, double yScale) {
        this.noise = (DensityFunction.NoiseHolder)Objects.requireNonNull(noise);
        this.xzScale = xzScale;
        this.yScale = yScale;
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.noise.getValue((double)x * this.xzScale, (double)y * this.yScale, (double)z * this.xzScale);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for(int i = 0; i < res.length; ++i) {
            res[i] = this.noise.getValue((double)x[i] * this.xzScale, (double)y[i] * this.yScale, (double)z[i] * this.xzScale);
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        m.load(1, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.xzScale);
        m.mul(Type.DOUBLE_TYPE);
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.yScale);
        m.mul(Type.DOUBLE_TYPE);
        m.load(3, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.xzScale);
        m.mul(Type.DOUBLE_TYPE);
        m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.xzScale);
            m.mul(Type.DOUBLE_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.yScale);
            m.mul(Type.DOUBLE_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.xzScale);
            m.mul(Type.DOUBLE_TYPE);
            m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DFTNoiseNode that = (DFTNoiseNode)o;
            return Double.compare(this.xzScale, that.xzScale) == 0 && Double.compare(this.yScale, that.yScale) == 0 && Objects.equals(this.noise, that.noise);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.noise.hashCode();
        result = 31 * result + Double.hashCode(this.xzScale);
        result = 31 * result + Double.hashCode(this.yScale);
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DFTNoiseNode that = (DFTNoiseNode)o;
            return Double.compare(this.xzScale, that.xzScale) == 0 && Double.compare(this.yScale, that.yScale) == 0;
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + Double.hashCode(this.xzScale);
        result = 31 * result + Double.hashCode(this.yScale);
        return result;
    }
}
