package io.canvasmc.canvas.dfc.common.ast.noise;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen.Context;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class ShiftedNoiseNode implements AstNode {
    private final AstNode shiftX;
    private final AstNode shiftY;
    private final AstNode shiftZ;
    private final double xzScale;
    private final double yScale;
    private final DensityFunction.NoiseHolder noise;

    public ShiftedNoiseNode(AstNode shiftX, AstNode shiftY, AstNode shiftZ, double xzScale, double yScale, DensityFunction.NoiseHolder noise) {
        this.shiftX = (AstNode)Objects.requireNonNull(shiftX);
        this.shiftY = (AstNode)Objects.requireNonNull(shiftY);
        this.shiftZ = (AstNode)Objects.requireNonNull(shiftZ);
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.noise = (DensityFunction.NoiseHolder)Objects.requireNonNull(noise);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double d = (double)x * this.xzScale + this.shiftX.evalSingle(x, y, z, type);
        double e = (double)y * this.yScale + this.shiftY.evalSingle(x, y, z, type);
        double f = (double)z * this.xzScale + this.shiftZ.evalSingle(x, y, z, type);
        return this.noise.getValue(d, e, f);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        double[] res1 = new double[res.length];
        double[] res2 = new double[res.length];
        this.shiftX.evalMulti(res, x, y, z, type);
        this.shiftY.evalMulti(res1, x, y, z, type);
        this.shiftZ.evalMulti(res2, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            res[i] = this.noise.getValue((double)x[i] * this.xzScale + res[i], (double)y[i] * this.yScale + res1[i], (double)z[i] * this.xzScale + res2[i]);
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.shiftX, this.shiftY, this.shiftZ};
    }

    public AstNode transform(AstTransformer transformer) {
        AstNode shiftX = this.shiftX.transform(transformer);
        AstNode shiftY = this.shiftY.transform(transformer);
        AstNode shiftZ = this.shiftZ.transform(transformer);
        return shiftX == this.shiftX && shiftY == this.shiftY && shiftZ == this.shiftZ ? transformer.transform(this) : transformer.transform(new ShiftedNoiseNode(shiftX, shiftY, shiftZ, this.xzScale, this.yScale, this.noise));
    }

    public void doBytecodeGenSingle(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        String shiftXMethod = context.newSingleMethod(this.shiftX);
        String shiftYMethod = context.newSingleMethod(this.shiftY);
        String shiftZMethod = context.newSingleMethod(this.shiftZ);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        m.load(1, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.xzScale);
        m.mul(Type.DOUBLE_TYPE);
        context.callDelegateSingle(m, shiftXMethod);
        m.add(Type.DOUBLE_TYPE);
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.yScale);
        m.mul(Type.DOUBLE_TYPE);
        context.callDelegateSingle(m, shiftYMethod);
        m.add(Type.DOUBLE_TYPE);
        m.load(3, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.xzScale);
        m.mul(Type.DOUBLE_TYPE);
        context.callDelegateSingle(m, shiftZMethod);
        m.add(Type.DOUBLE_TYPE);
        m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        String shiftXMethod = context.newMultiMethod(this.shiftX);
        String shiftYMethod = context.newMultiMethod(this.shiftY);
        String shiftZMethod = context.newMultiMethod(this.shiftZ);
        int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));
        int res2 = localVarConsumer.createLocalVariable("res2", Type.getDescriptor(double[].class));
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(0);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), new Type[]{Type.INT_TYPE, Type.BOOLEAN_TYPE}), false);
        m.store(res1, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(0);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), new Type[]{Type.INT_TYPE, Type.BOOLEAN_TYPE}), false);
        m.store(res2, InstructionAdapter.OBJECT_TYPE);
        context.callDelegateMulti(m, shiftXMethod);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(context.className, shiftYMethod, Context.MULTI_DESC, false);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(res2, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(context.className, shiftZMethod, Context.MULTI_DESC, false);
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
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.add(Type.DOUBLE_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.yScale);
            m.mul(Type.DOUBLE_TYPE);
            m.load(res1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.add(Type.DOUBLE_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.xzScale);
            m.mul(Type.DOUBLE_TYPE);
            m.load(res2, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.add(Type.DOUBLE_TYPE);
            m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class)}), false);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(res2, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class)}), false);
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ShiftedNoiseNode that = (ShiftedNoiseNode)o;
            return Double.compare(this.xzScale, that.xzScale) == 0 && Double.compare(this.yScale, that.yScale) == 0 && Objects.equals(this.shiftX, that.shiftX) && Objects.equals(this.shiftY, that.shiftY) && Objects.equals(this.shiftZ, that.shiftZ) && Objects.equals(this.noise, that.noise);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.shiftX.hashCode();
        result = 31 * result + this.shiftY.hashCode();
        result = 31 * result + this.shiftZ.hashCode();
        result = 31 * result + Double.hashCode(this.xzScale);
        result = 31 * result + Double.hashCode(this.yScale);
        result = 31 * result + this.noise.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ShiftedNoiseNode that = (ShiftedNoiseNode)o;
            return Double.compare(this.xzScale, that.xzScale) == 0 && Double.compare(this.yScale, that.yScale) == 0 && this.shiftX.relaxedEquals(that.shiftX) && this.shiftY.relaxedEquals(that.shiftY) && this.shiftZ.relaxedEquals(that.shiftZ);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.shiftX.relaxedHashCode();
        result = 31 * result + this.shiftY.relaxedHashCode();
        result = 31 * result + this.shiftZ.relaxedHashCode();
        result = 31 * result + Double.hashCode(this.xzScale);
        result = 31 * result + Double.hashCode(this.yScale);
        return result;
    }
}
