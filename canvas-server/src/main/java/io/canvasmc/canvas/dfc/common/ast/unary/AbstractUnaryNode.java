package io.canvasmc.canvas.dfc.common.ast.unary;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Objects;
import org.objectweb.asm.commons.InstructionAdapter;

public abstract class AbstractUnaryNode implements AstNode {
    protected final AstNode operand;

    public AbstractUnaryNode(AstNode operand) {
        this.operand = (AstNode)Objects.requireNonNull(operand);
    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.operand};
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractUnaryNode that = (AbstractUnaryNode)o;
            return Objects.equals(this.operand, that.operand);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.operand.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractUnaryNode that = (AbstractUnaryNode)o;
            return this.operand.relaxedEquals(that.operand);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.operand.relaxedHashCode();
        return result;
    }

    protected abstract AstNode newInstance(AstNode var1);

    public AstNode transform(AstTransformer transformer) {
        AstNode operand = this.operand.transform(transformer);
        return this.operand == operand ? transformer.transform(this) : transformer.transform(this.newInstance(operand));
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String operandMethod = context.newSingleMethod(this.operand);
        context.callDelegateSingle(m, operandMethod);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String operandMethod = context.newMultiMethod(this.operand);
        context.callDelegateMulti(m, operandMethod);
    }
}
