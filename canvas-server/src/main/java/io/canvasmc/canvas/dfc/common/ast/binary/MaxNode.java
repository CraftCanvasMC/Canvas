package io.canvasmc.canvas.dfc.common.ast.binary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class MaxNode extends AbstractBinaryNode {
    public MaxNode(AstNode left, AstNode right) {
        super(left, right);
    }

    protected AstNode newInstance(AstNode left, AstNode right) {
        return new MaxNode(left, right);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return Math.max(this.left.evalSingle(x, y, z, type), this.right.evalSingle(x, y, z, type));
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        double[] res1 = new double[res.length];
        this.left.evalMulti(res, x, y, z, type);
        this.right.evalMulti(res1, x, y, z, type);

        for(int i = 0; i < res1.length; ++i) {
            res[i] = Math.max(res[i], res1[i]);
        }

    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenSingle(context, m, localVarConsumer);
        m.invokestatic(Type.getInternalName(Math.class), "max", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
        m.areturn(Type.DOUBLE_TYPE);
    }

    protected void bytecodeGenMultiBody(InstructionAdapter m, int idx, int res1) {
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.load(idx, Type.INT_TYPE);
        m.dup2();
        m.aload(Type.DOUBLE_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.load(idx, Type.INT_TYPE);
        m.aload(Type.DOUBLE_TYPE);
        m.invokestatic(Type.getInternalName(Math.class), "max", Type.getMethodDescriptor(Type.DOUBLE_TYPE, new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE}), false);
        m.astore(Type.DOUBLE_TYPE);
    }
}
