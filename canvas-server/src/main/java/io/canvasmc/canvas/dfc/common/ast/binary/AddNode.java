package io.canvasmc.canvas.dfc.common.ast.binary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class AddNode extends AbstractBinaryNode {
    public AddNode(AstNode left, AstNode right) {
        super(left, right);
    }

    protected AstNode newInstance(AstNode left, AstNode right) {
        return new AddNode(left, right);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.left.evalSingle(x, y, z, type) + this.right.evalSingle(x, y, z, type);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        double[] res1 = new double[res.length];
        this.left.evalMulti(res, x, y, z, type);
        this.right.evalMulti(res1, x, y, z, type);

        for(int i = 0; i < res1.length; ++i) {
            res[i] += res1[i];
        }

    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        super.doBytecodeGenSingle(context, m, localVarConsumer);
        m.add(Type.DOUBLE_TYPE);
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
        m.add(Type.DOUBLE_TYPE);
        m.astore(Type.DOUBLE_TYPE);
    }
}
