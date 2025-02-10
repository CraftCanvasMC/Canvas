package io.canvasmc.canvas.dfc.common.ast;

import io.canvasmc.canvas.dfc.common.gen.BytecodeGen;
import org.objectweb.asm.commons.InstructionAdapter;

public interface AstNode {
    double evalSingle(int var1, int var2, int var3, EvalType var4);

    void evalMulti(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5);

    AstNode[] getChildren();

    AstNode transform(AstTransformer var1);

    void doBytecodeGenSingle(BytecodeGen.Context var1, InstructionAdapter var2, BytecodeGen.Context.LocalVarConsumer var3);

    void doBytecodeGenMulti(BytecodeGen.Context var1, InstructionAdapter var2, BytecodeGen.Context.LocalVarConsumer var3);

    boolean relaxedEquals(AstNode var1);

    int relaxedHashCode();
}
