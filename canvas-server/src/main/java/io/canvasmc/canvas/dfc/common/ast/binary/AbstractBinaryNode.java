package io.canvasmc.canvas.dfc.common.ast.binary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen.Context;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public abstract class AbstractBinaryNode implements AstNode {
    protected final AstNode left;
    protected final AstNode right;

    public AbstractBinaryNode(AstNode left, AstNode right) {
        this.left = (AstNode)Objects.requireNonNull(left);
        this.right = (AstNode)Objects.requireNonNull(right);
    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.left, this.right};
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractBinaryNode that = (AbstractBinaryNode)o;
            return Objects.equals(this.left, that.left) && Objects.equals(this.right, that.right);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.left.hashCode();
        result = 31 * result + this.right.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractBinaryNode that = (AbstractBinaryNode)o;
            return this.left.relaxedEquals(that.left) && this.right.relaxedEquals(that.right);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.left.relaxedHashCode();
        result = 31 * result + this.right.relaxedHashCode();
        return result;
    }

    protected abstract AstNode newInstance(AstNode var1, AstNode var2);

    public AstNode transform(AstTransformer transformer) {
        AstNode left = this.left.transform(transformer);
        AstNode right = this.right.transform(transformer);
        return left == this.left && right == this.right ? transformer.transform(this) : transformer.transform(this.newInstance(left, right));
    }

    public void doBytecodeGenSingle(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String leftMethod = context.newSingleMethod(this.left);
        String rightMethod = context.newSingleMethod(this.right);
        context.callDelegateSingle(m, leftMethod);
        context.callDelegateSingle(m, rightMethod);
    }

    public void doBytecodeGenMulti(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String leftMethod = context.newMultiMethod(this.left);
        String rightMethod = context.newMultiMethod(this.right);
        int res1 = localVarConsumer.createLocalVariable("res1", Type.getDescriptor(double[].class));
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.arraylength();
        m.iconst(0);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "getDoubleArray", Type.getMethodDescriptor(Type.getType(double[].class), new Type[]{Type.INT_TYPE, Type.BOOLEAN_TYPE}), false);
        m.store(res1, InstructionAdapter.OBJECT_TYPE);
        context.callDelegateMulti(m, leftMethod);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, InstructionAdapter.OBJECT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.load(5, InstructionAdapter.OBJECT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(context.className, rightMethod, Context.MULTI_DESC, false);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            this.bytecodeGenMultiBody(m, idx, res1);
        });
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.load(res1, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ArrayCache.class), "recycle", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class)}), false);
        m.areturn(Type.VOID_TYPE);
    }

    protected abstract void bytecodeGenMultiBody(InstructionAdapter var1, int var2, int var3);
}
