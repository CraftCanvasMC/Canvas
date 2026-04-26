package io.canvasmc.canvas.configuration;

public interface Resolver<C> {
    default void onDiffAdd(String fullyQualifiedName) {
    }

    default void onDiffRemove(String fullyQualifiedName) {
    }

    void onFinishLoad(C instance);
}
