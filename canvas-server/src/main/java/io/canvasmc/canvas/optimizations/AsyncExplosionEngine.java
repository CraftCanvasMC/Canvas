package io.canvasmc.canvas.optimizations;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canvas 核心异步 TNT 爆炸引擎。
 * <p>
 * 直接使用 NMS 数据结构：
 * - 所有方块查询直接走 {@code Level.getBlockState(BlockPos)}
 * - 实体遍历直接走 {@code Level.getEntities()}
 * - ForkJoinPool 并行射线追踪
 * - 结果通过 {@code RegionScheduler} 写回
 * <p>
 * 线程安全：所有方块/实体修改在区域线程执行；射线追踪在 ForkJoinPool 执行。
 */
public final class AsyncExplosionEngine {
    private static final ForkJoinPool ASYNC_POOL;
    private static final int RAY_GRID = 16;
    private static final int FULL_RAY_COUNT;
    private static final double[][] RAY_DIRECTIONS;
    private static final float STEP = 0.3F;
    private static final float DECAY = 0.225F;

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
                    dirs.add(new double[] { dx / len, dy / len, dz / len });
                }
            }
        }
        RAY_DIRECTIONS = dirs.toArray(new double[0][]);
        FULL_RAY_COUNT = RAY_DIRECTIONS.length;

        int cores = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.min(cores, Math.max(4, cores * 3 / 4));
        ASYNC_POOL = new ForkJoinPool(poolSize, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    private static final ThreadLocal<Random> RNG = ThreadLocal.withInitial(Random::new);

    public static ForkJoinPool getPool() { return ASYNC_POOL; }

    public static CompletableFuture<ExplosionResult> processAsync(
            ServerLevel level, Vec3 center, float power, boolean causeFire,
            Explosion.BlockInteraction blockInteraction, Object explosionSource) {

        return CompletableFuture.supplyAsync(() -> {
            int intPower = (int) Math.ceil(power);
            BlockPos centerPos = BlockPos.containing(center);

            // 1. 射线追踪: 收集破坏方块
            Set<BlockPos> destroyed = ConcurrentHashMap.newKeySet();
            int poolSize = ASYNC_POOL.getParallelism();
            int batchCount = poolSize * 2;
            int batchSize = Math.max(1, FULL_RAY_COUNT / batchCount);

            List<CompletableFuture<Void>> rayTasks = new ArrayList<>();
            for (int start = 0; start < FULL_RAY_COUNT; start += batchSize) {
                int end = Math.min(start + batchSize, FULL_RAY_COUNT);
                rayTasks.add(CompletableFuture.runAsync(() -> {
                    Random rng = RNG.get();
                    for (int i = start; i < end; i++) {
                        double[] dir = RAY_DIRECTIONS[i];
                        float dirX = (float) (dir[0] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);
                        float dirY = (float) (dir[1] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);
                        float dirZ = (float) (dir[2] + (rng.nextFloat() - rng.nextFloat()) * 0.0075F * 0.6F);
                        float currentPower = power * (0.7F + rng.nextFloat() * 0.6F);
                        float curX = (float) center.x;
                        float curY = (float) center.y;
                        float curZ = (float) center.z;
                        while (currentPower > 0.0F) {
                            BlockPos pos = BlockPos.containing(curX, curY, curZ);
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir()) {
                                float absorption = (state.getBlock().getExplosionResistance() + 0.3F) * 0.3F;
                                currentPower -= absorption;
                                if (currentPower > 0.0F) destroyed.add(pos.immutable());
                            }
                            curX += dirX * STEP;
                            curY += dirY * STEP;
                            curZ += dirZ * STEP;
                            currentPower -= DECAY;
                        }
                    }
                }, ASYNC_POOL));
            }

            // 2. 实体伤害
            AABB area = new AABB(centerPos).inflate(power * 2.0);
            List<Entity> entities = new ArrayList<>(level.getEntities(null, area, e -> e instanceof LivingEntity));
            List<CompletableFuture<EntityDamageResult>> entityTasks = new ArrayList<>();

            if (!entities.isEmpty()) {
                int eBatchCount = Math.min(poolSize, entities.size());
                int eBatchSize = Math.max(1, entities.size() / eBatchCount);
                for (int start = 0; start < entities.size(); start += eBatchSize) {
                    int end = Math.min(start + eBatchSize, entities.size());
                    entityTasks.add(CompletableFuture.supplyAsync(() -> {
                        List<EntityDamageResult> results = new ArrayList<>();
                        for (int i = start; i < end; i++) {
                            Entity entity = entities.get(i);
                            Vec3 entityPos = entity.position();
                            double dist = entityPos.distanceTo(center) / (power * 2.0);
                            if (dist > 1.0) continue;
                            double exposure = computeExposure(level, center, entity, intPower);
                            double damage = (power * power + power) * 0.5 * 7.0 * (power * 2.0) + 1.0;
                            damage *= (1.0 - dist) * exposure;
                            Vec3 knockback = entityPos.subtract(center).normalize();
                            double kbPower = (1.0 - dist) * exposure * (1.0 - ((LivingEntity) entity).getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
                            knockback = knockback.scale(kbPower);
                            results.add(new EntityDamageResult(entity.getUUID(), (float) damage, knockback));
                        }
                        return results;
                    }, ASYNC_POOL));
                }
            }

            // 3. 等待全部完成
            for (CompletableFuture<Void> f : rayTasks) f.join();
            List<EntityDamageResult> allDamage = new ArrayList<>();
            for (CompletableFuture<List<EntityDamageResult>> f : entityTasks) {
                allDamage.addAll(f.join());
            }

            return new ExplosionResult(new ArrayList<>(destroyed), allDamage, causeFire, blockInteraction);
        }, ASYNC_POOL);
    }

    private static double computeExposure(Level level, Vec3 center, Entity entity, int intPower) {
        Vec3 eye = entity.getEyePosition();
        double dx = (eye.x - center.x) / intPower;
        double dy = (eye.y - center.y) / intPower;
        double dz = (eye.z - center.z) / intPower;

        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (steps <= 0) return 1.0;

        double stepX = dx / steps, stepY = dy / steps, stepZ = dz / steps;
        int misses = 0;
        double curX = center.x + stepX * 0.5;
        double curY = center.y + stepY * 0.5;
        double curZ = center.z + stepZ * 0.5;

        for (int i = 0; i < steps; i++) {
            BlockPos check = BlockPos.containing(curX, curY, curZ);
            BlockState state = level.getBlockState(check);
            if (state.isAir() || !state.isSolid()) misses++;
            curX += stepX; curY += stepY; curZ += stepZ;
        }

        return (double) misses / steps;
    }

    public record ExplosionResult(
            List<BlockPos> destroyedBlocks,
            List<EntityDamageResult> entityDamage,
            boolean causeFire,
            Explosion.BlockInteraction blockInteraction) {}

    public record EntityDamageResult(java.util.UUID entityId, float damage, Vec3 knockback) {}
}
