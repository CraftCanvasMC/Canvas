package io.canvasmc.canvas.dfc.common.ast.binary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen.Context;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class MulNode extends AbstractBinaryNode {
    public MulNode(AstNode left, AstNode right) {
        super(left, right);
    }

    protected AstNode newInstance(AstNode left, AstNode right) {
        return new MulNode(left, right);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double evaled = this.left.evalSingle(x, y, z, type);
        return evaled == 0.0 ? 0.0 : evaled * this.right.evalSingle(x, y, z, type);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.left.evalMulti(res, x, y, z, type);

        for(int i = 0; i < res.length; ++i) {
            res[i] = res[i] == 0.0 ? 0.0 : res[i] * this.right.evalSingle(x[i], y[i], z[i], type);
        }

    }

    public void doBytecodeGenSingle(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String leftMethod = context.newSingleMethod(this.left);
        String rightMethod = context.newSingleMethod(this.right);
        Label notZero = new Label();
        context.callDelegateSingle(m, leftMethod);
        m.dup2();
        m.dconst(0.0);
        m.cmpl(Type.DOUBLE_TYPE);
        m.ifne(notZero);
        m.dconst(0.0);
        m.areturn(Type.DOUBLE_TYPE);
        m.visitLabel(notZero);
        context.callDelegateSingle(m, rightMethod);
        m.mul(Type.DOUBLE_TYPE);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String leftMethod = context.newMultiMethod(this.left);
        String rightMethodSingle = context.newSingleMethod(this.right);
        context.callDelegateMulti(m, leftMethod);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            Label minLabel = new Label();
            Label end = new Label();
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.dup2();
            m.dconst(0.0);
            m.cmpl(Type.DOUBLE_TYPE);
            m.ifne(minLabel);
            m.pop2();
            m.dconst(0.0);
            m.goTo(end);
            m.visitLabel(minLabel);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.load(5, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(context.className, rightMethodSingle, Context.SINGLE_DESC, false);
            m.mul(Type.DOUBLE_TYPE);
            m.visitLabel(end);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    protected void bytecodeGenMultiBody(InstructionAdapter m, int idx, int res1) {
        throw new UnsupportedOperationException();
    }
}
