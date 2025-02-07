package io.canvasmc.canvas;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import io.netty.util.internal.PlatformDependent;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;
import java.util.Locale;
import java.util.function.BiFunction;

public enum ChunkSystemAlgorithm {
    MOONRISE((configWorkerThreads, configIoThreads) -> {
        int defaultWorkerThreads = Runtime.getRuntime().availableProcessors() / 2;
        if (defaultWorkerThreads <= 4) {
            defaultWorkerThreads = defaultWorkerThreads <= 3 ? 1 : 2;
        } else {
            defaultWorkerThreads = defaultWorkerThreads / 2;
        }
        defaultWorkerThreads = Integer.getInteger(PlatformHooks.get().getBrand() + ".WorkerThreadCount", Integer.valueOf(defaultWorkerThreads));

        int workerThreads = configWorkerThreads;

        if (workerThreads <= 0) {
            workerThreads = defaultWorkerThreads;
        }
        final int ioThreads = Math.max(1, configIoThreads);
        return new Pair<>(workerThreads, ioThreads);
    }),
    C2ME((_, configIoThreads) -> {
        String expression = """
                
                    max(
                        1,
                        min(
                            if( is_windows,
                                (cpus / 1.6),
                                (cpus / 1.3)
                            )  - if(is_client, 1, 0),
                            ( ( mem_gb - (if(is_client, 1.0, 0.5)) ) / 0.6 )
                        )
                    )
                \040""";
        int eval = tryEvaluateExpression(expression);
        return new Pair<>(eval, Math.max(1, configIoThreads));
    }),
    ALL((_, configIoThreads) -> {
        final int ioThreads = Math.max(1, configIoThreads);
        return new Pair<>(Runtime.getRuntime().availableProcessors(), ioThreads);
    }),
    ANY((configWorkerThreads, configIoThreads) -> {
        int workerThreads = configWorkerThreads;
        if (configWorkerThreads <= 0) {
            // y=(-0.0001x^2)+0.8831x-5.1544
            int p = Runtime.getRuntime().availableProcessors();
            workerThreads = (int) Math.round((-0.0001 * Math.pow(p, 2)) + (0.8831 * p) - 5.1544);
        }
        return new Pair<>(workerThreads, Math.max(1, configIoThreads));
    });

    private static int tryEvaluateExpression(String expression) {
        return (int) Math.max(1,
            new ExpressionBuilder(expression)
                .variables("is_windows", "is_j9vm", "is_client", "cpus", "mem_gb")
                .function(new Function("max", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.max(args[0], args[1]);
                    }
                })
                .function(new Function("min", 2) {
                    @Override
                    public double apply(double... args) {
                        return Math.min(args[0], args[1]);
                    }
                })
                .function(new Function("if", 3) {
                    @Override
                    public double apply(double... args) {
                        return args[0] != 0 ? args[1] : args[2];
                    }
                })
                .build()
                .setVariable("is_windows", PlatformDependent.isWindows() ? 1 : 0)
                .setVariable("is_j9vm", PlatformDependent.isJ9Jvm() ? 1 : 0)
                .setVariable("is_client", 0)
                .setVariable("cpus", Runtime.getRuntime().availableProcessors())
                .setVariable("mem_gb", Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0 / 1024.0)
                .evaluate()
        );
    }

    private final BiFunction<Integer, Integer, Pair<Integer, Integer>> eval;

    ChunkSystemAlgorithm(BiFunction<Integer, Integer, Pair<Integer, Integer>> eval) {
        this.eval = eval;
    }

    public static ChunkSystemAlgorithm fromRaw(@NotNull String raw) {
        String capital = raw.toUpperCase(Locale.ROOT);
        return valueOf(capital);
    }

    public int evalWorkers(final int configWorkerThreads, final int configIoThreads) {
        return eval.apply(configWorkerThreads, configIoThreads).getA();
    }

    public int evalIO(final int configWorkerThreads, final int configIoThreads) {
        return eval.apply(configWorkerThreads, configIoThreads).getB();
    }

    public @NotNull String asDebugString() {
        return this + "(" + evalWorkers(-1, -1) + ")";
    }
}
