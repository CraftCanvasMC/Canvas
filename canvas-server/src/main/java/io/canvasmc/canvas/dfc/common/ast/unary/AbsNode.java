package io.canvasmc.canvas.dfc.common.ast.unary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class AbsNode extends AbstractUnaryNode {
    public AbsNode(AstNode operand) {
        super(operand);
    }

    protected AstNode newInstance(AstNode operand) {
        return new AbsNode(operand);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return Math.abs(this.operand.evalSingle(x, y, z, type));
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.operand.evalMulti(res, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            res[i] = Math.abs(res[i]);
        }

    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenSingle(context, m, localVarConsumer);
        m.invokestatic(Type.getInternalName(Math.class), "abs", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE}), false);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenMulti(context, m, localVarConsumer);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.dup2();
            m.aload(Type.DOUBLE_TYPE);
            m.invokestatic(Type.getInternalName(Math.class), "abs", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE}), false);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }
}
