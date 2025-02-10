package io.canvasmc.canvas.dfc.common.ast.noise;

import io.canvasmc.canvas.dfc.common.IDensityFunctionsCaveScaler;
import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class DFTWeirdScaledSamplerNode implements AstNode {
    private final AstNode input;
    private final DensityFunction.NoiseHolder noise;
    private final DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper;

    public DFTWeirdScaledSamplerNode(AstNode input, DensityFunction.NoiseHolder noise, DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper) {
        this.input = Objects.requireNonNull(input);
        this.noise = Objects.requireNonNull(noise);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double v = this.input.evalSingle(x, y, z, type);
        double d = (this.mapper.mapper).get(v);
        return d * Math.abs(this.noise.getValue((double)x / d, (double)y / d, (double)z / d));
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.input.evalMulti(res, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            double d = (this.mapper.mapper).get(res[i]);
            res[i] = d * Math.abs(this.noise.getValue((double)x[i] / d, (double)y[i] / d, (double)z[i] / d));
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.input};
    }

    public AstNode transform(AstTransformer transformer) {
        AstNode input = this.input.transform(transformer);
        return input == this.input ? transformer.transform(this) : transformer.transform(new DFTWeirdScaledSamplerNode(input, this.noise, this.mapper));
    }

    public void doBytecodeGenSingle(BytecodeGen.@NotNull Context context, InstructionAdapter m, BytecodeGen.Context.@NotNull LocalVarConsumer localVarConsumer) {
        String inputMethod = context.newSingleMethod(this.input);
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        int scale = localVarConsumer.createLocalVariable("scale", Type.DOUBLE_TYPE.getDescriptor());
        context.callDelegateSingle(m, inputMethod);
        switch (this.mapper) {
            case TYPE1 -> m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleTunnels", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
            case TYPE2 -> m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleCaves", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
            default -> throw new UnsupportedOperationException(String.format("Unknown mapper %s", this.mapper));
        }

        m.store(scale, Type.DOUBLE_TYPE);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        m.load(1, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.load(scale, Type.DOUBLE_TYPE);
        m.div(Type.DOUBLE_TYPE);
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.load(scale, Type.DOUBLE_TYPE);
        m.div(Type.DOUBLE_TYPE);
        m.load(3, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.load(scale, Type.DOUBLE_TYPE);
        m.div(Type.DOUBLE_TYPE);
        m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
        m.invokestatic(Type.getInternalName(Math.class), "abs", "(D)D", false);
        m.load(scale, Type.DOUBLE_TYPE);
        m.mul(Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String inputMethod = context.newMultiMethod(this.input);
        String noiseField = context.newField(DensityFunction.NoiseHolder.class, this.noise);
        context.callDelegateMulti(m, inputMethod);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            int scale = localVarConsumer.createLocalVariable("scale", Type.DOUBLE_TYPE.getDescriptor());
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            switch (this.mapper) {
                case TYPE1 -> m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleTunnels", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
                case TYPE2 -> m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleCaves", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
                default -> throw new UnsupportedOperationException(String.format("Unknown mapper %s", this.mapper));
            }

            m.store(scale, Type.DOUBLE_TYPE);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(context.className, noiseField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.load(scale, Type.DOUBLE_TYPE);
            m.div(Type.DOUBLE_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.load(scale, Type.DOUBLE_TYPE);
            m.div(Type.DOUBLE_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.load(scale, Type.DOUBLE_TYPE);
            m.div(Type.DOUBLE_TYPE);
            m.invokevirtual(Type.getInternalName(DensityFunction.NoiseHolder.class), "getValue", "(DDD)D", false);
            m.invokestatic(Type.getInternalName(Math.class), "abs", "(D)D", false);
            m.load(scale, Type.DOUBLE_TYPE);
            m.mul(Type.DOUBLE_TYPE);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DFTWeirdScaledSamplerNode that = (DFTWeirdScaledSamplerNode)o;
            return Objects.equals(this.input, that.input) && Objects.equals(this.noise, that.noise) && this.mapper == that.mapper;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.hashCode();
        result = 31 * result + this.noise.hashCode();
        result = 31 * result + this.mapper.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            DFTWeirdScaledSamplerNode that = (DFTWeirdScaledSamplerNode)o;
            return this.input.relaxedEquals(that.input) && this.mapper == that.mapper;
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.relaxedHashCode();
        result = 31 * result + this.mapper.hashCode();
        return result;
    }
}
