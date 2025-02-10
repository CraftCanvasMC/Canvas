package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import java.util.Arrays;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class ConstantNode implements AstNode {
    private final double value;

    public ConstantNode(double value) {
        this.value = value;
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return this.value;
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        Arrays.fill(res, this.value);
    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        m.dconst(this.value);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.dconst(this.value);
        m.invokestatic(Type.getInternalName(Arrays.class), "fill", Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(double[].class), Type.DOUBLE_TYPE}), false);
        m.areturn(Type.VOID_TYPE);
    }

    public double getValue() {
        return this.value;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ConstantNode that = (ConstantNode)o;
            return Double.compare(this.value, that.value) == 0;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Double.hashCode(this.value);
    }

    public boolean relaxedEquals(AstNode o) {
        return this.equals(o);
    }

    public int relaxedHashCode() {
        return this.hashCode();
    }
}
