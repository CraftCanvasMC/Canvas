package io.canvasmc.canvas.dfc.common.ast.misc;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.AstTransformer;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import io.canvasmc.canvas.dfc.common.gen.BytecodeGen.Context;
import java.util.Objects;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

public class RangeChoiceNode implements AstNode {
    private final AstNode input;
    private final double minInclusive;
    private final double maxExclusive;
    private final AstNode whenInRange;
    private final AstNode whenOutOfRange;

    public RangeChoiceNode(AstNode input, double minInclusive, double maxExclusive, AstNode whenInRange, AstNode whenOutOfRange) {
        this.input = (AstNode)Objects.requireNonNull(input);
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.whenInRange = (AstNode)Objects.requireNonNull(whenInRange);
        this.whenOutOfRange = (AstNode)Objects.requireNonNull(whenOutOfRange);
    }

    public double evalSingle(int x, int y, int z, EvalType type) {
        double v = this.input.evalSingle(x, y, z, type);
        return v >= this.minInclusive && v < this.maxExclusive ? this.whenInRange.evalSingle(x, y, z, type) : this.whenOutOfRange.evalSingle(x, y, z, type);
    }

    public void evalMulti(double[] res, int[] x, int[] y, int[] z, EvalType type) {
        this.input.evalMulti(res, x, y, z, type);
        int numInRange = 0;

        int numOutOfRange;
        for(numOutOfRange = 0; numOutOfRange < x.length; ++numOutOfRange) {
            double v = res[numOutOfRange];
            if (v >= this.minInclusive && v < this.maxExclusive) {
                ++numInRange;
            }
        }

        numOutOfRange = res.length - numInRange;
        if (numInRange == 0) {
            this.evalChildMulti(this.whenOutOfRange, res, x, y, z, type);
        } else if (numInRange == res.length) {
            this.evalChildMulti(this.whenInRange, res, x, y, z, type);
        } else {
            int idx1 = 0;
            int[] i1 = new int[numInRange];
            double[] res1 = new double[numInRange];
            int[] x1 = new int[numInRange];
            int[] y1 = new int[numInRange];
            int[] z1 = new int[numInRange];
            int idx2 = 0;
            int[] i2 = new int[numOutOfRange];
            double[] res2 = new double[numOutOfRange];
            int[] x2 = new int[numOutOfRange];
            int[] y2 = new int[numOutOfRange];
            int[] z2 = new int[numOutOfRange];

            int i;
            for(i = 0; i < res.length; ++i) {
                double v = res[i];
                int index;
                if (v >= this.minInclusive && v < this.maxExclusive) {
                    index = idx1++;
                    i1[index] = i;
                    x1[index] = x[i];
                    y1[index] = y[i];
                    z1[index] = z[i];
                } else {
                    index = idx2++;
                    i2[index] = i;
                    x2[index] = x[i];
                    y2[index] = y[i];
                    z2[index] = z[i];
                }
            }

            this.evalChildMulti(this.whenInRange, res1, x1, y1, z1, type);
            this.evalChildMulti(this.whenOutOfRange, res2, x2, y2, z2, type);

            for(i = 0; i < numInRange; ++i) {
                res[i1[i]] = res1[i];
            }

            for(i = 0; i < numOutOfRange; ++i) {
                res[i2[i]] = res2[i];
            }
        }

    }

    public static void evalMultiStatic(double[] res, int[] x, int[] y, int[] z, EvalType type, double minInclusive, double maxExclusive, BytecodeGen.EvalSingleInterface whenInRangeSingle, BytecodeGen.EvalSingleInterface whenOutOfRangeSingle, BytecodeGen.EvalMultiInterface inputMulti, BytecodeGen.EvalMultiInterface whenInRangeMulti, BytecodeGen.EvalMultiInterface whenOutOfRangeMulti) {
        inputMulti.evalMulti(res, x, y, z, type);
        int numInRange = 0;

        int numOutOfRange;
        for(numOutOfRange = 0; numOutOfRange < x.length; ++numOutOfRange) {
            double v = res[numOutOfRange];
            if (v >= minInclusive && v < maxExclusive) {
                ++numInRange;
            }
        }

        numOutOfRange = res.length - numInRange;
        if (numInRange == 0) {
            evalChildMulti(whenOutOfRangeSingle, whenOutOfRangeMulti, res, x, y, z, type);
        } else if (numInRange == res.length) {
            evalChildMulti(whenInRangeSingle, whenInRangeMulti, res, x, y, z, type);
        } else {
            int idx1 = 0;
            int[] i1 = new int[numInRange];
            double[] res1 = new double[numInRange];
            int[] x1 = new int[numInRange];
            int[] y1 = new int[numInRange];
            int[] z1 = new int[numInRange];
            int idx2 = 0;
            int[] i2 = new int[numOutOfRange];
            double[] res2 = new double[numOutOfRange];
            int[] x2 = new int[numOutOfRange];
            int[] y2 = new int[numOutOfRange];
            int[] z2 = new int[numOutOfRange];

            int i;
            for(i = 0; i < res.length; ++i) {
                double v = res[i];
                int index;
                if (v >= minInclusive && v < maxExclusive) {
                    index = idx1++;
                    i1[index] = i;
                    x1[index] = x[i];
                    y1[index] = y[i];
                    z1[index] = z[i];
                } else {
                    index = idx2++;
                    i2[index] = i;
                    x2[index] = x[i];
                    y2[index] = y[i];
                    z2[index] = z[i];
                }
            }

            evalChildMulti(whenInRangeSingle, whenInRangeMulti, res1, x1, y1, z1, type);
            evalChildMulti(whenOutOfRangeSingle, whenOutOfRangeMulti, res2, x2, y2, z2, type);

            for(i = 0; i < numInRange; ++i) {
                res[i1[i]] = res1[i];
            }

            for(i = 0; i < numOutOfRange; ++i) {
                res[i2[i]] = res2[i];
            }
        }

    }

    private static void evalChildMulti(BytecodeGen.EvalSingleInterface single, BytecodeGen.EvalMultiInterface multi, double[] res, int[] x, int[] y, int[] z, EvalType type) {
        if (res.length == 1) {
            res[0] = single.evalSingle(x[0], y[0], z[0], type);
        } else {
            multi.evalMulti(res, x, y, z, type);
        }

    }

    private void evalChildMulti(AstNode child, double[] res, int[] x, int[] y, int[] z, EvalType type) {
        if (res.length == 1) {
            res[0] = child.evalSingle(x[0], y[0], z[0], type);
        } else {
            child.evalMulti(res, x, y, z, type);
        }

    }

    public AstNode[] getChildren() {
        return new AstNode[]{this.input, this.whenInRange, this.whenOutOfRange};
    }

    public AstNode transform(AstTransformer transformer) {
        AstNode input = this.input.transform(transformer);
        AstNode whenInRange = this.whenInRange.transform(transformer);
        AstNode whenOutOfRange = this.whenOutOfRange.transform(transformer);
        return this.input == input && this.whenInRange == whenInRange && this.whenOutOfRange == whenOutOfRange ? transformer.transform(this) : transformer.transform(new RangeChoiceNode(input, this.minInclusive, this.maxExclusive, whenInRange, whenOutOfRange));
    }

    public void doBytecodeGenSingle(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String inputMethod = context.newSingleMethod(this.input);
        String whenInRangeMethod = context.newSingleMethod(this.whenInRange);
        String whenOutOfRangeMethod = context.newSingleMethod(this.whenOutOfRange);
        int inputValue = localVarConsumer.createLocalVariable("inputValue", Type.DOUBLE_TYPE.getDescriptor());
        context.callDelegateSingle(m, inputMethod);
        m.store(inputValue, Type.DOUBLE_TYPE);
        Label whenOutOfRangeLabel = new Label();
        Label end = new Label();
        m.load(inputValue, Type.DOUBLE_TYPE);
        m.dconst(this.minInclusive);
        m.cmpl(Type.DOUBLE_TYPE);
        m.iflt(whenOutOfRangeLabel);
        m.load(inputValue, Type.DOUBLE_TYPE);
        m.dconst(this.maxExclusive);
        m.cmpg(Type.DOUBLE_TYPE);
        m.ifge(whenOutOfRangeLabel);
        if (whenInRangeMethod.equals(inputMethod)) {
            m.load(inputValue, Type.DOUBLE_TYPE);
        } else {
            context.callDelegateSingle(m, whenInRangeMethod);
        }

        m.goTo(end);
        m.visitLabel(whenOutOfRangeLabel);
        if (whenOutOfRangeMethod.equals(inputMethod)) {
            m.load(inputValue, Type.DOUBLE_TYPE);
        } else {
            context.callDelegateSingle(m, whenOutOfRangeMethod);
        }

        m.visitLabel(end);
        m.areturn(Type.DOUBLE_TYPE);
    }

    public void doBytecodeGenMulti(Context context, InstructionAdapter m, Context.LocalVarConsumer localVarConsumer) {
        String inputSingle = context.newSingleMethod(this.input);
        String whenInRangeSingle = context.newSingleMethod(this.whenInRange);
        String whenOutOfRangeSingle = context.newSingleMethod(this.whenOutOfRange);
        String inputMulti = context.newMultiMethod(this.input);
        context.callDelegateMulti(m, inputMulti);
        context.doCountedLoop(m, localVarConsumer, (idx) -> {
            Label whenOutOfRangeLabel = new Label();
            Label end = new Label();
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.dconst(this.minInclusive);
            m.cmpl(Type.DOUBLE_TYPE);
            m.iflt(whenOutOfRangeLabel);
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(idx, Type.INT_TYPE);
            m.aload(Type.DOUBLE_TYPE);
            m.dconst(this.maxExclusive);
            m.cmpg(Type.DOUBLE_TYPE);
            m.ifge(whenOutOfRangeLabel);
            if (whenInRangeSingle.equals(inputSingle)) {
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.DOUBLE_TYPE);
            } else {
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
                m.invokevirtual(context.className, whenInRangeSingle, Context.SINGLE_DESC, false);
            }

            m.goTo(end);
            m.visitLabel(whenOutOfRangeLabel);
            if (whenOutOfRangeSingle.equals(inputSingle)) {
                m.load(1, InstructionAdapter.OBJECT_TYPE);
                m.load(idx, Type.INT_TYPE);
                m.aload(Type.DOUBLE_TYPE);
            } else {
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
                m.invokevirtual(context.className, whenOutOfRangeSingle, Context.SINGLE_DESC, false);
            }

            m.visitLabel(end);
            m.astore(Type.DOUBLE_TYPE);
        });
        m.areturn(Type.VOID_TYPE);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            RangeChoiceNode that = (RangeChoiceNode)o;
            return Double.compare(this.minInclusive, that.minInclusive) == 0 && Double.compare(this.maxExclusive, that.maxExclusive) == 0 && Objects.equals(this.input, that.input) && Objects.equals(this.whenInRange, that.whenInRange) && Objects.equals(this.whenOutOfRange, that.whenOutOfRange);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.hashCode();
        result = 31 * result + Double.hashCode(this.minInclusive);
        result = 31 * result + Double.hashCode(this.maxExclusive);
        result = 31 * result + this.whenInRange.hashCode();
        result = 31 * result + this.whenOutOfRange.hashCode();
        return result;
    }

    public boolean relaxedEquals(AstNode o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            RangeChoiceNode that = (RangeChoiceNode)o;
            return Double.compare(this.minInclusive, that.minInclusive) == 0 && Double.compare(this.maxExclusive, that.maxExclusive) == 0 && this.input.relaxedEquals(that.input) && this.whenInRange.relaxedEquals(that.whenInRange) && this.whenOutOfRange.relaxedEquals(that.whenOutOfRange);
        } else {
            return false;
        }
    }

    public int relaxedHashCode() {
        int result = 1;
        result = 31 * result + this.getClass().hashCode();
        result = 31 * result + this.input.relaxedHashCode();
        result = 31 * result + Double.hashCode(this.minInclusive);
        result = 31 * result + Double.hashCode(this.maxExclusive);
        result = 31 * result + this.whenInRange.relaxedHashCode();
        result = 31 * result + this.whenOutOfRange.relaxedHashCode();
        return result;
    }
}
