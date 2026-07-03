package io.canvasmc.canvas.configuration;

public interface Resolver<C> {
    default void onDiffAdd(final String fullyQualifiedName) {
    }

    default void onDiffRemove(final String fullyQualifiedName) {
    }

    void onFinishLoad(final C instance);
}
