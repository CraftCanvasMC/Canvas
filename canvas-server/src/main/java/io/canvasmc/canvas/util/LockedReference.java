package io.canvasmc.canvas.util;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.Nullable;

/**
 * A wrapper of a generic type {@code E} that is modified via swap operations, in a way where upon mutations the object
 * being replaced with should not be the same as the previous object. Sort of a "copy, mutate, paste" style
 * <p>
 * All mutators should return a new object or copy of the previous object to maintain consistency with the intent of
 * this utility class
 *
 * @param <E>
 *     the generic type element
 *
 * @author dueris
 */
public class LockedReference<E> {
    private final ReentrantLock writeLock = new ReentrantLock(true);

    @UnknownNullability
    private E value;

    public LockedReference(final @Nullable E default_) {
        this.value = default_;
    }

    public LockedReference() {
        this.value = null;
    }

    /**
     * Gets this reference as an {@link java.util.Optional}, returning an empty optional if the value is null when
     * calling
     *
     * @return the reference wrapped as an optional
     */
    public Optional<E> asOptional() {
        return Optional.ofNullable(getValue());
    }

    /**
     * Gets if the value currently is set
     *
     * @return {@code true} if the value is set, {@code false} otherwise
     */
    public boolean isSet() {
        return value != null;
    }

    /**
     * Sets the value to {@code null} via {@link io.canvasmc.canvas.util.LockedReference#swapValue(UnaryOperator)}
     */
    public void unset() {
        swapValue((_) -> null);
    }

    /**
     * Sets the value to the new value provided
     *
     * @param newValue
     *     the new value
     */
    public void swapValue(final @Nullable E newValue) {
        swapValue((_) -> newValue);
    }

    /**
     * Sets the value to the new value via a unary operator. The returned value from the operator MUST be a new object
     *
     * @param operator
     *     the operator to apply, the returned value can be {@code null}
     */
    public void swapValue(final UnaryOperator<@Nullable E> operator) {
        writeLock.lock();
        try {
            value = operator.apply(value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Sets the value to the new value via a unary operator. The returned value from the operator MUST be a new object
     *
     * @param operator
     *     the operator to apply, the returned value can be {@code null}
     *
     * @return the new value
     */
    public E swapAndGet(final UnaryOperator<E> operator) {
        writeLock.lock();
        try {
            return value = operator.apply(value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Gets the current value
     *
     * @return the value
     */
    @UnknownNullability
    public E getValue() {
        return value;
    }
}
