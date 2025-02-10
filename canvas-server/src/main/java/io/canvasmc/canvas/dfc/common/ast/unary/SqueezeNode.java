package io.canvasmc.canvas.dfc.common.ast.unary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import net.minecraft.util.Mth;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class SqueezeNode extends AbstractUnaryNode {
    public SqueezeNode(AstNode operand) {
        super(operand);
    }

    protected AstNode newInstance(AstNode operand) {
        return new SqueezeNode(operand);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double v = Mth.clamp(this.operand.evalSingle(x, y, z, type), -1.0, 1.0);
        return v / 2.0 - v * v * v / 24.0;
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.operand.evalMulti(res, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            double v = Mth.clamp(res[i], -1.0, 1.0);
            res[i] = v / 2.0 - v * v * v / 24.0;
        }

    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenSingle(context, m, localVarConsumer);
        m.dconst(1.0);
        m.invokestatic(Type.getInternalName(Math.class), "min", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
        m.dconst(-1.0);
        m.invokestatic(Type.getInternalName(Math.class), "max", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
        int v = localVarConsumer.createLocalVariable("v", Type.DOUBLE_TYPE.getDescriptor());
        m.store(v, Type.DOUBLE_TYPE);
        m.load(v, Type.DOUBLE_TYPE);
        m.dconst(2.0);
        m.div(Type.DOUBLE_TYPE);
        m.load(v, Type.DOUBLE_TYPE);
        m.dup2();
        m.dup2();
        m.mul(Type.DOUBLE_TYPE);
        m.mul(Type.DOUBLE_TYPE);
        m.dconst(24.0);
        m.div(Type.DOUBLE_TYPE);
        m.sub(Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenMulti(context, m, localVarConsumer);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            int v = localVarConsumer.createLocalVariable("v", Type.DOUBLE_TYPE.getDescriptor());
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.dup2();
            m.aload(Type.DOUBLE_TYPE);
            m.dconst(1.0);
            m.invokestatic(Type.getInternalName(Math.class), "min", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
            m.dconst(-1.0);
            m.invokestatic(Type.getInternalName(Math.class), "max", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
            m.store(v, Type.DOUBLE_TYPE);
            m.load(v, Type.DOUBLE_TYPE);
            m.dconst(2.0);
            m.div(Type.DOUBLE_TYPE);
            m.load(v, Type.DOUBLE_TYPE);
            m.dup2();
            m.dup2();
            m.mul(Type.DOUBLE_TYPE);
            m.mul(Type.DOUBLE_TYPE);
            m.dconst(24.0);
            m.div(Type.DOUBLE_TYPE);
            m.sub(Type.DOUBLE_TYPE);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }
}
