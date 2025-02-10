package io.canvasmc.canvas.dfc.common.gen;

import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import java.util.List;

public interface CompiledEntry extends ISingleMethod, IMultiMethod {
    double evalSingle(int var1, int var2, int var3, EvalType var4);

    void evalMulti(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5, ArrayCache var6);

    CompiledEntry newInstance(List<?> var1);

    List<Object> getArgs();
}
