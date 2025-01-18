package io.canvasmc.canvas.util;

import java.util.concurrent.ConcurrentLinkedDeque;

public class ConcurrentLinkedDequeTree<E> extends ConcurrentLinkedDeque<E> {

    public E first() {
        return super.getFirst();
    }
}
