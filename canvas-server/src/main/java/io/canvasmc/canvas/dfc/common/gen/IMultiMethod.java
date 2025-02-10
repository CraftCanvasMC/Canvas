package io.canvasmc.canvas.dfc.common.gen;

import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;

@FunctionalInterface
public interface IMultiMethod {
    void evalMulti(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5, ArrayCache var6);
}
