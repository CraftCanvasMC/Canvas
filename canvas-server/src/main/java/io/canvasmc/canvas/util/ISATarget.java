package io.canvasmc.canvas.util;

import io.canvasmc.canvas.util.isa.ISA_aarch64;
import io.canvasmc.canvas.util.isa.ISA_x86_64;

public interface ISATarget {

    static Class<? extends Enum<? extends ISATarget>> getInstance() {
        return switch (NativeLoader.NORMALIZED_ARCH) {
            case "x86_64" -> ISA_x86_64.class;
            case "aarch_64" -> ISA_aarch64.class;
            default -> null;
        };
    }
    int ordinal();
    String getSuffix();
    boolean isNativelySupported();

}
