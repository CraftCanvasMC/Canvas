package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class RootNode implements AstNode {
    private final AstNode next;

    public RootNode(AstNode next) {
        this.next = (AstNode)Objects.requireNonNull(next);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.next.evalSingle(x, y, z, type);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.next.evalMulti(res, x, y, z, type);
    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.next};
    }

    public AstNode transform(AstTransformer transformer) {
        AstNode next = this.next.transform(transformer);
        return next == this.next ? transformer.transform(this) : transformer.transform(new RootNode(next));
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String nextMethod = context.newSingleMethod(this.next);
        context.callDelegateSingle(m, nextMethod);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        String nextMethod = context.newMultiMethod(this.next);
        context.callDelegateMulti(m, nextMethod);
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            RootNode that = (RootNode)o;
            return Objects.equals(this.next, that.next);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.next.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            RootNode that = (RootNode)o;
            return this.next.relaxedEquals(that.next);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.next.relaxedHashCode();
        return result;
    }
}
