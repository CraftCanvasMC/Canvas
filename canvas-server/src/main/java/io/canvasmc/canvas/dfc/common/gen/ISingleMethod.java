package io.canvasmc.canvas.dfc.common.gen;

import io.canvasmc.canvas.dfc.common.ast.EvalType;

@FunctionalInterface
public interface ISingleMethod {
    double evalSingle(int var1, int var2, int var3, EvalType var4);
}
