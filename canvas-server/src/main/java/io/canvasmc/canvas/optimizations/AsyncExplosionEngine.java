package io.canvasmc.canvas.optimizations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.*;

/**
 * Canvas 核心异步 TNT 爆炸引擎 — v2 Snapshot-Based Parallel Ray Tracing。
 * <p>
 * 设计理念（最小侵入 + 最大安全）：
 * <ul>
 *   <li>只并行化 CPU 密集部分：1352 条射线的方块追踪</li>
 *   <li>实体伤害 / 方块破坏 / CraftBukkit 事件 全部保留原版逻辑</li>
 *   <li>每个并行任务使用独立的块缓存（无共享可变状态）</li>
 *   <li>使用 ThreadLocalRandom 替代 level.getRandom()（线程安全）</li>
 * </ul>
 * <p>
 * 调用方必须在区域线程上调用，方法内部将射线追踪分发到 ForkJoinPool。
 * 方法阻塞等待结果后返回 List<BlockPos>，调用方继续走原版的
 * hurtEntities() + interactWithBlocks() + createFire() 流程。
 */
public final class AsyncExplosionEngine {

    private static final ForkJoinPool ASYNC_POOL;
    private static final int RAY_GRID = 16;
    private static final int FULL_RAY_COUNT;
    private static final double[][] RAY_DIRECTIONS;
    private static final float STEP = 0.3F;
    private static final float DECAY = 0.22500001F;

    // Paper uses the same 16x16x16 grid, precomputed as triplets
    // We mirror it here for our parallel batches
    static {
        List<double[]> dirs = new ArrayList<>();
        for (int tx = 0; tx < RAY_GRID; tx++) {
            for (int ty = 0; ty < RAY_GRID; ty++) {
                for (int tz = 0; tz < RAY_GRID; tz++) {
                    if (tx != 0 && tx != RAY_GRID - 1
                            && ty != 0 && ty != RAY_GRID - 1
                            && tz != 0 && tz != RAY_GRID - 1) continue;
                    double dx = (tx / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double dy = (ty / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double dz = (tz / (double) (RAY_GRID - 1)) * 2.0 - 1.0;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dirs.add(new double[]{dx / len, dy / len, dz / len});
                }
            }
        }
        RAY_DIRECTIONS = dirs.toArray(new double[0][]);
        FULL_RAY_COUNT = RAY_DIRECTIONS.length;

        int cores = Runtime.getRuntime().availableProcessors();
        // 智能线程数：min(cores, max(4, cores*3/4))，避免与 Folia/Moonrise/GC 抢核心
        int poolSize = Math.min(cores, Math.max(4, cores * 3 / 4));
        ASYNC_POOL = new ForkJoinPool(poolSize,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    public static ForkJoinPool getPool() { return ASYNC_POOL; }

    /**
     * 并行射线追踪 — 在区域线程上调用，阻塞等待全部射线完成后返回。
     * <p>
     * 每个并行批次使用独立的块缓存，无共享可变状态。
     * 块查询直接调用 level.getBlockState()（已加载区块的 PalettedContainer 读操作线程安全）。
     *
     * @param level     服务器世界
     * @param center    爆炸中心
     * @param radius    爆炸半径（TNT = 4.0）
     * @param canExplode 块是否可被爆炸破坏的判断器（来自 ExplosionDamageCalculator）
     * @return 被破坏的方块位置列表
     */
    public static List<BlockPos> parallelRayTrace(
            ServerLevel level, Vec3 center, float radius,
            BlockExplodeChecker canExplode) {

        // 分批：每个批次处理若干条射线
        int parallelism = ASYNC_POOL.getParallelism();
        int batchCount = Math.min(parallelism * 2, FULL_RAY_COUNT);
        int batchSize = Math.max(1, (FULL_RAY_COUNT + batchCount - 1) / batchCount);

        // 预加载爆炸范围内所有区块（确保不会触发异步区块加载）
        int blockRadius = (int) Math.ceil(radius * 2) + 2;
        int minCX = (Mth.floor(center.x) - blockRadius) >> 4;
        int maxCX = (Mth.floor(center.x) + blockRadius) >> 4;
        int minCZ = (Mth.floor(center.z) - blockRadius) >> 4;
        int maxCZ = (Mth.floor(center.z) + blockRadius) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (level.hasChunk(cx, cz)) {
                    level.getChunk(cx, cz); // 确保区块已加载
                }
            }
        }

        // 并行射线追踪
        List<CompletableFuture<Set<BlockPos>>> futures = new ArrayList<>();
        for (int start = 0; start < FULL_RAY_COUNT; start += batchSize) {
            final int rayStart = start;
            final int rayEnd = Math.min(start + batchSize, FULL_RAY_COUNT);
            futures.add(CompletableFuture.supplyAsync(() -> {
                Set<BlockPos> localDestroyed = new HashSet<>();
                Random rng = ThreadLocalRandom.current();

                for (int i = rayStart; i < rayEnd; i++) {
                    double[] dir = RAY_DIRECTIONS[i];
                    float currentPower = radius * (0.7F + rng.nextFloat() * 0.6F);
                    double curX = center.x;
                    double curY = center.y;
                    double curZ = center.z;

                    // 方向微量扰动（与 Paper 原版一致）
                    float dirX = (float) (dir[0] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);
                    float dirY = (float) (dir[1] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);
                    float dirZ = (float) (dir[2] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);

                    while (currentPower > 0.0F) {
                        int bx = Mth.floor(curX);
                        int by = Mth.floor(curY);
                        int bz = Mth.floor(curZ);

                        BlockPos pos = new BlockPos(bx, by, bz);

                        // 边界检查
                        if (!level.isInWorldBounds(pos)) break;

                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            float absorption = (state.getBlock().getExplosionResistance() + 0.3F) * 0.3F;
                            currentPower -= absorption;
                            if (currentPower > 0.0F && canExplode.shouldExplode(state)) {
                                localDestroyed.add(pos.immutable());
                            }
                        }

                        currentPower -= DECAY;
                        curX += dirX * STEP;
                        curY += dirY * STEP;
                        curZ += dirZ * STEP;
                    }
                }
                return localDestroyed;
            }, ASYNC_POOL));
        }

        // 合并结果
        List<BlockPos> result = new ArrayList<>();
        for (CompletableFuture<Set<BlockPos>> f : futures) {
            try {
                Set<BlockPos> batch = f.get(5, TimeUnit.SECONDS);
                if (batch != null) result.addAll(batch);
            } catch (Exception e) {
                // 单批次失败不影响整体，记录日志后继续
                org.apache.logging.log4j.LogManager.getLogger("Canvas/AsyncExplosion")
                        .warn("Parallel ray batch failed", e);
            }
        }
        return result;
    }

    /**
     * 方块可破坏性检查器 — 由 ServerExplosion 的 damageCalculator 驱动。
     */
    @FunctionalInterface
    public interface BlockExplodeChecker {
        boolean shouldExplode(BlockState state);
    }
}
