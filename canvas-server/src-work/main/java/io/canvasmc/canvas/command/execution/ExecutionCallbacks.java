package io.canvasmc.canvas.command.execution;

import java.util.function.Function;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("unused")
public interface ExecutionCallbacks {

    @Contract(pure = true)
    @NonNull
    static <A extends Number, B extends Number> Function<NumericPair<A, B>, NumericPair<A, B>> mapPairFirst(
        Function<A, ? extends Number> fa
    ) {
        return pair -> pair.mapFirst(fa);
    }

    @Contract(pure = true)
    @NonNull
    static <A extends Number, B extends Number> Function<NumericPair<A, B>, NumericPair<A, B>> mapPairSecond(
        Function<B, ? extends Number> fb
    ) {
        return pair -> pair.mapSecond(fb);
    }

    @Contract(pure = true)
    @NonNull
    static <A extends Number, B extends Number> Function<NumericPair<A, B>, NumericPair<A, B>> mapPairBoth(
        Function<A, ? extends Number> fa, Function<B, ? extends Number> fb
    ) {
        return pair -> pair.mapBoth(fa, fb);
    }

    @Contract(pure = true)
    @NonNull
    static <A, B> Function<A, B> nothing() {
        //noinspection unchecked
        return (a) -> (B) a;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> incrementInt() {
        return (a) -> a + 1;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> decrement() {
        return (a) -> a - 1;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> addInt(final int amount) {
        return (a) -> a + amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> subtractInt(final int amount) {
        return (a) -> a - amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> multiplyInt(final int amount) {
        return (a) -> a * amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Integer, Integer> divideInt(final int amount) {
        return (a) -> a / amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> incrementLong() {
        return (a) -> a + 1L;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> decrementLong() {
        return (a) -> a - 1L;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> addLong(final long amount) {
        return (a) -> a + amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> subtractLong(final long amount) {
        return (a) -> a - amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> multiplyLong(final long amount) {
        return (a) -> a * amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Long, Long> divideLong(final long amount) {
        return (a) -> a / amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> incrementFloat() {
        return (a) -> a + 1.0F;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> decrementFloat() {
        return (a) -> a - 1.0F;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> addFloat(final float amount) {
        return (a) -> a + amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> subtractFloat(final float amount) {
        return (a) -> a - amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> multiplyFloat(final float amount) {
        return (a) -> a * amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Float, Float> divideFloat(final float amount) {
        return (a) -> a / amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> incrementDouble() {
        return (a) -> a + 1.0D;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> decrementDouble() {
        return (a) -> a - 1.0D;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> addDouble(final double amount) {
        return (a) -> a + amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> subtractDouble(final double amount) {
        return (a) -> a - amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> multiplyDouble(final double amount) {
        return (a) -> a * amount;
    }

    @Contract(pure = true)
    @NonNull
    static Function<Double, Double> divideDouble(final double amount) {
        return (a) -> a / amount;
    }

    record NumericPair<A extends Number, B extends Number>(A first, B second) {

        @Contract("_, _ -> new")
        public static <A extends Number, B extends Number> @NonNull NumericPair<A, B> of(A first, B second) {
            return new NumericPair<>(first, second);
        }

        @Contract("_ -> new")
        @SuppressWarnings("unchecked")
        public @NonNull NumericPair<A, B> mapFirst(@NonNull Function<A, ? extends Number> f) {
            return new NumericPair<>((A) f.apply(this.first), this.second);
        }

        @Contract("_ -> new")
        @SuppressWarnings("unchecked")
        public @NonNull NumericPair<A, B> mapSecond(@NonNull Function<B, ? extends Number> f) {
            return new NumericPair<>(this.first, (B) f.apply(this.second));
        }

        public @NonNull NumericPair<A, B> mapBoth(Function<A, ? extends Number> fa, Function<B, ? extends Number> fb) {
            return mapFirst(fa).mapSecond(fb);
        }
    }
}
