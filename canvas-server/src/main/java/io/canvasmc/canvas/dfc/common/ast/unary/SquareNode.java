package io.canvasmc.canvas.dfc.common.ast.unary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class SquareNode extends AbstractUnaryNode {
    public SquareNode(AstNode operand) {
        super(operand);
    }

    protected AstNode newInstance(AstNode operand) {
        return new SquareNode(operand);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double v = this.operand.evalSingle(x, y, z, type);
        return v * v;
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.operand.evalMulti(res, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            res[i] *= res[i];
        }

    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenSingle(context, m, localVarConsumer);
        m.dup2();
        m.mul(Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenMulti(context, m, localVarConsumer);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.dup2();
            m.aload(Type.DOUBLE_TYPE);
            m.dup2();
            m.mul(Type.DOUBLE_TYPE);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }
}
