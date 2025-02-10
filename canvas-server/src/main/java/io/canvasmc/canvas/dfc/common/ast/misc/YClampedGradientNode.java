package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import net.minecraft.util.Mth;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class YClampedGradientNode implements AstNode {
    private final double fromY;
    private final double toY;
    private final double fromValue;
    private final double toValue;

    public YClampedGradientNode(double fromY, double toY, double fromValue, double toValue) {
        this.fromY = fromY;
        this.toY = toY;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        return Mth.clampedMap((double)y, this.fromY, this.toY, this.fromValue, this.toValue);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        for(int i = 0; i < res.length; ++i) {
            res[i] = Mth.clampedMap((double)y[i], this.fromY, this.toY, this.fromValue, this.toValue);
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[0];
    }

    public AstNode transform(AstTransformer transformer) {
        return transformer.transform(this);
    }

    public void doBytecodeGenSingle(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(this.fromY);
        m.dconst(this.toY);
        m.dconst(this.fromValue);
        m.dconst(this.toValue);
        m.invokestatic(Type.getInternalName(Mth.class), "clampedMap", "(DDDDD)D", false);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(BytecodeGen.Context context, InstructionAdapter m, BytecodeGen.Context.LocalVarConsumer localVarConsumer) {
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(3, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(this.fromY);
            m.dconst(this.toY);
            m.dconst(this.fromValue);
            m.dconst(this.toValue);
            m.invokestatic(Type.getInternalName(Mth.class), "clampedMap", "(DDDDD)D", false);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            YClampedGradientNode that = (YClampedGradientNode)o;
            return Double.compare(this.fromY, that.fromY) == 0 && Double.compare(this.toY, that.toY) == 0 && Double.compare(this.fromValue, that.fromValue) == 0 && Double.compare(this.toValue, that.toValue) == 0;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + Double.hashCode(this.fromY);
        result = 31 * result + Double.hashCode(this.toY);
        result = 31 * result + Double.hashCode(this.fromValue);
        result = 31 * result + Double.hashCode(this.toValue);
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        return this.equals(o);
    }

    public int relaxedHashCode() {
        return this.hashCode();
    }
}
