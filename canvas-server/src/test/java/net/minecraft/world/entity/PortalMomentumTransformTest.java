package net.minecraft.world.entity;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PortalMomentumTransformTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void asyncPortalUsesLiveSourceMotionInsteadOfCopiedNbtMotion() {
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        doNothing().when(entity).transform(any(Vec3.class), any(Float.class), any(Float.class), any(Vec3.class));
        Vec3 target = new Vec3(8.5, 70.0, -3.5);
        Vec3 liveVelocity = new Vec3(12.5, 0.25, -11.0);
        PositionMoveRotation source = new PositionMoveRotation(Vec3.ZERO, liveVelocity, 90.0F, 17.0F);
        TeleportTransition transition = new TeleportTransition(
            mock(ServerLevel.class),
            target,
            Vec3.ZERO,
            90.0F,
            0.0F,
            Relative.union(Relative.DELTA, Set.of(Relative.X_ROT)),
            TeleportTransition.DO_NOTHING
        );

        entity.transformForPortal(source, transition, false);

        verify(entity).transform(target, 90.0F, 17.0F, liveVelocity);
    }

    @Test
    void endPortalPreservesWorldSpaceMotionAndPitchForEachEntity() {
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        doNothing().when(entity).transform(any(Vec3.class), any(Float.class), any(Float.class), any(Vec3.class));
        Vec3 target = new Vec3(100.5, 50.0, 0.5);
        Vec3 liveVelocity = new Vec3(11.25, 2.5, -13.75);
        PositionMoveRotation source = new PositionMoveRotation(Vec3.ZERO, liveVelocity, 0.0F, -23.0F);
        TeleportTransition transition = new TeleportTransition(
            mock(ServerLevel.class),
            target,
            Vec3.ZERO,
            90.0F,
            0.0F,
            Set.of(),
            TeleportTransition.DO_NOTHING
        );

        entity.transformForPortal(source, transition, true);

        verify(entity).transform(target, 90.0F, -23.0F, liveVelocity);
    }

    @Test
    void missingNetherExitFramePreservesWorldSpaceMotion() {
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        doNothing().when(entity).transform(any(Vec3.class), any(Float.class), any(Float.class), any(Vec3.class));
        Vec3 target = new Vec3(-32.5, 72.0, 48.5);
        Vec3 liveVelocity = new Vec3(6.75, -0.5, -4.25);
        PositionMoveRotation source = new PositionMoveRotation(Vec3.ZERO, liveVelocity, 15.0F, 11.0F);
        TeleportTransition transition = new TeleportTransition(
            mock(ServerLevel.class),
            target,
            Vec3.ZERO,
            90.0F,
            0.0F,
            Relative.union(Relative.direction(true, true, true), Set.of(Relative.X_ROT)),
            TeleportTransition.DO_NOTHING
        );

        entity.transformForPortal(source, transition, false);

        verify(entity).transform(target, 90.0F, 11.0F, liveVelocity);
    }
}
