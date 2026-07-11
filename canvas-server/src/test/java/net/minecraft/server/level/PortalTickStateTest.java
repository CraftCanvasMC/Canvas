package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.entity.activation.ActivationRange;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Portal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

public class PortalTickStateTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nonPlayerPortalEntryStartsPrePhysicsHandling() {
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);

        entity.setAsInsidePortal(mock(Portal.class), BlockPos.ZERO);

        assertNotNull(entity.portalProcess);
        assertEquals(1, entity.canvas$portalTickState);
    }

    @Test
    void playerPortalEntryDoesNotUseNonPlayerTickState() {
        Player player = mock(Player.class, CALLS_REAL_METHODS);

        player.setAsInsidePortal(mock(Portal.class), BlockPos.ZERO);

        assertNotNull(player.portalProcess);
        assertEquals(0, player.canvas$portalTickState);
    }

    @Test
    void directPortalHandlerClearsStateBeforeAsyncHandoff() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(16, 64, 16);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();
        doReturn(true).when(entity).canUsePortal(false);
        doReturn(300).when(entity).getDimensionChangingDelay();
        when(portal.getPortalTransitionTime(level, entity)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(true);
        when(portal.portalAsync(level, entity, entryPosition)).thenAnswer(invocation -> {
            assertEquals(0, entity.canvas$portalTickState);
            return true;
        });

        boolean portalled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(true);
            portalled = entity.handlePortal();
        }

        assertTrue(portalled);
        assertEquals(0, entity.canvas$portalTickState);
        assertFalse(processor.isInsidePortalThisTick());
        assertEquals(1, processor.getPortalTime());
        assertEquals(300, entity.getPortalCooldown());
    }

    @Test
    void committedPortalFailureDoesNotAdvertiseAFakeRetry() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(-16, 70, 16);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();
        doReturn(true).when(entity).canUsePortal(false);
        doReturn(300).when(entity).getDimensionChangingDelay();
        when(portal.getPortalTransitionTime(level, entity)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(true);
        when(portal.portalAsync(level, entity, entryPosition)).thenReturn(false);

        boolean portalled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(true);
            portalled = entity.handlePortal();
        }

        assertFalse(portalled);
        assertEquals(0, entity.canvas$portalTickState);
        assertFalse(processor.isInsidePortalThisTick());
        assertEquals(1, processor.getPortalTime());
        assertEquals(300, entity.getPortalCooldown());
    }

    @Test
    void wrongEntityOwnerDefersWithoutConsumingPortalState() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        PortalProcessor processor = new PortalProcessor(portal, BlockPos.ZERO);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();

        boolean handled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(false);
            handled = entity.handlePortal();
        }

        assertTrue(handled);
        assertEquals(1, entity.canvas$portalTickState);
        assertTrue(processor.isInsidePortalThisTick());
        assertEquals(0, processor.getPortalTime());
        assertEquals(0, entity.getPortalCooldown());
        verify(portal, never()).getPortalTransitionTime(level, entity);
        verify(portal, never()).portalAsync(level, entity, BlockPos.ZERO);
    }

    @Test
    void wrongPortalBlockOwnerDefersThenRetriesWithoutConsumingPortalState() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(32, 64, 32);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();
        doReturn(true).when(entity).canUsePortal(false);
        doReturn(300).when(entity).getDimensionChangingDelay();
        when(portal.getPortalTransitionTime(level, entity)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(true);
        when(portal.portalAsync(level, entity, entryPosition)).thenReturn(true);

        boolean deferred;
        boolean portalled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(false, true);
            deferred = entity.handlePortal();

            assertTrue(deferred);
            assertEquals(1, entity.canvas$portalTickState);
            assertTrue(processor.isInsidePortalThisTick());
            assertEquals(0, processor.getPortalTime());
            assertEquals(0, entity.getPortalCooldown());
            verify(portal, never()).portalAsync(level, entity, entryPosition);

            portalled = entity.handlePortal();
        }

        assertTrue(portalled);
        assertEquals(0, entity.canvas$portalTickState);
        assertFalse(processor.isInsidePortalThisTick());
        assertEquals(1, processor.getPortalTime());
        assertEquals(300, entity.getPortalCooldown());
        verify(portal).portalAsync(level, entity, entryPosition);
    }

    @Test
    void permanentlySplitPortalRegionAbandonsAfterABoundedRetry() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(48, 64, -48);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();
        doReturn(true).when(entity).canUsePortal(false);
        when(portal.getPortalTransitionTime(level, entity)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(true);

        boolean deferred;
        boolean sameTickDeferred;
        boolean handled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(false);
            deferred = entity.handlePortal();
            sameTickDeferred = entity.handlePortal();

            assertTrue(deferred);
            assertTrue(sameTickDeferred);
            assertEquals(processor, entity.portalProcess);
            assertEquals(1, entity.canvas$portalTickState);
            assertTrue(processor.isInsidePortalThisTick());
            assertEquals(0, processor.getPortalTime());
            assertEquals(0, entity.getPortalCooldown());

            entity.tickCount++;
            handled = entity.handlePortal();
        }

        assertFalse(handled);
        assertNull(entity.portalProcess);
        assertEquals(0, entity.canvas$portalTickState);
        assertTrue(processor.isInsidePortalThisTick());
        assertEquals(0, processor.getPortalTime());
        assertEquals(0, entity.getPortalCooldown());
        verify(portal, never()).portalAsync(level, entity, entryPosition);
    }

    @Test
    void playerDoesNotCarryAStalePortalMarkerAcrossTicks() {
        ServerLevel level = mock(ServerLevel.class);
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(-80, 70, -80);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        player.portalProcess = processor;
        doReturn(level).when(player).level();
        doReturn(true).when(player).canUsePortal(false);
        when(portal.getPortalTransitionTime(level, player)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(true);

        boolean handled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(player)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(false);
            handled = player.handlePortal();
        }

        assertFalse(handled);
        assertNull(player.portalProcess);
        assertTrue(processor.isInsidePortalThisTick());
        assertEquals(0, processor.getPortalTime());
        assertEquals(0, player.getPortalCooldown());
        verify(portal, never()).portalAsync(level, player, entryPosition);
    }

    @Test
    void portalWithoutSourceBlockReadsCommitsFromTheEntityOwner() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class, CALLS_REAL_METHODS);
        Portal portal = mock(Portal.class);
        BlockPos entryPosition = new BlockPos(80, 60, 80);
        PortalProcessor processor = new PortalProcessor(portal, entryPosition);
        entity.portalProcess = processor;
        entity.canvas$portalTickState = 1;
        doReturn(level).when(entity).level();
        doReturn(true).when(entity).canUsePortal(false);
        doReturn(300).when(entity).getDimensionChangingDelay();
        when(portal.getPortalTransitionTime(level, entity)).thenReturn(0);
        when(portal.requiresPortalEntryBlockOwnership()).thenReturn(false);
        when(portal.portalAsync(level, entity, entryPosition)).thenReturn(true);

        boolean portalled;
        try (MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)) {
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(level, entryPosition)).thenReturn(false);
            portalled = entity.handlePortal();
        }

        assertTrue(portalled);
        assertEquals(0, entity.canvas$portalTickState);
        assertFalse(processor.isInsidePortalThisTick());
        assertEquals(1, processor.getPortalTime());
        assertEquals(300, entity.getPortalCooldown());
        verify(portal).portalAsync(level, entity, entryPosition);
    }

    @Test
    void pendingPortalIsHandledBeforeAnotherPhysicsTick() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 1;
        when(entity.handlePortal()).thenReturn(true);

        tickAsActiveEntity(level, entity, true);

        verify(entity, never()).tick();
        verify(entity).handlePortal();
        assertEquals(0, entity.canvas$portalTickState);
    }

    @Test
    void ownershipDeferralRemainsPendingAfterThePrePhysicsCheck() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 1;
        when(entity.handlePortal()).thenAnswer(invocation -> {
            entity.canvas$portalTickState = 1;
            return true;
        });

        tickAsActiveEntity(level, entity, true);

        verify(entity, never()).tick();
        verify(entity).handlePortal();
        assertEquals(1, entity.canvas$portalTickState);
    }

    @Test
    void failedPendingPortalSchedulesCompensation() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 1;
        when(entity.getPassengers()).thenReturn(List.of());
        when(entity.handlePortal()).thenReturn(false);

        tickAsActiveEntity(level, entity, true);

        verify(entity, never()).tick();
        verify(entity).handlePortal();
        assertEquals(2, entity.canvas$portalTickState);
    }

    @Test
    void pendingPortalRemainsPendingUntilItsRegionOwnsTheEntity() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 1;

        tickAsActiveEntity(level, entity, false);

        verify(entity, never()).tick();
        verify(entity, never()).handlePortal();
        assertEquals(1, entity.canvas$portalTickState);
    }

    @Test
    void failedPrePhysicsPortalCompensatesTheSkippedTickInOrder() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 2;
        when(entity.getPassengers()).thenReturn(List.of());
        when(entity.handlePortal()).thenReturn(false);

        tickAsActiveEntity(level, entity, true);

        InOrder order = inOrder(entity);
        order.verify(entity).tick();
        order.verify(entity).handlePortal();
        order.verify(entity).tick();
        order.verify(entity).handlePortal();
        assertEquals(0, entity.canvas$portalTickState);
    }

    @Test
    void successfulFirstCompensationStopsImmediately() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 2;
        when(entity.handlePortal()).thenReturn(true);

        tickAsActiveEntity(level, entity, true);

        verify(entity).tick();
        verify(entity).handlePortal();
        verify(entity, times(1)).tick();
        assertEquals(0, entity.canvas$portalTickState);
    }

    @Test
    void compensationStopsWhenTheFirstTickChangesRegionOwnership() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 2;

        tickAsActiveEntity(level, entity, false);

        verify(entity).tick();
        verify(entity, never()).handlePortal();
        assertEquals(2, entity.canvas$portalTickState);
    }

    @Test
    void sameRegionPortalEntryIsConsumedByTheImmediatePortalCheck() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        doAnswer(invocation -> {
            entity.canvas$portalTickState = 1;
            return null;
        }).when(entity).tick();
        when(entity.handlePortal()).thenReturn(true);

        tickAsActiveEntity(level, entity, true);

        verify(entity).tick();
        verify(entity).handlePortal();
        assertEquals(0, entity.canvas$portalTickState);
    }

    @Test
    void secondCompensationTickKeepsANewPortalStateAcrossRegionHandoff() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 2;
        final int[] ticks = {0};
        doAnswer(invocation -> {
            if (++ticks[0] == 2) entity.canvas$portalTickState = 1;
            return null;
        }).when(entity).tick();
        when(entity.handlePortal()).thenReturn(false);

        try (
            MockedStatic<ActivationRange> activationRange = mockStatic(ActivationRange.class);
            MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)
        ) {
            activationRange.when(() -> ActivationRange.checkIfActive(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true, false);

            level.tickNonPassenger(entity);
        }

        verify(entity, times(2)).tick();
        verify(entity, times(1)).handlePortal();
        assertEquals(1, entity.canvas$portalTickState);
    }

    @Test
    void completedCompensationDoesNotRepeatAfterARegionHandoff() {
        ServerLevel level = mock(ServerLevel.class, CALLS_REAL_METHODS);
        Entity entity = mock(Entity.class);
        entity.canvas$portalTickState = 2;
        when(entity.handlePortal()).thenReturn(false);

        try (
            MockedStatic<ActivationRange> activationRange = mockStatic(ActivationRange.class);
            MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)
        ) {
            activationRange.when(() -> ActivationRange.checkIfActive(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(true, false);

            level.tickNonPassenger(entity);
        }

        verify(entity, times(2)).tick();
        verify(entity, times(1)).handlePortal();
        assertEquals(0, entity.canvas$portalTickState);
    }

    private static void tickAsActiveEntity(final ServerLevel level, final Entity entity, final boolean ownsEntity) {
        try (
            MockedStatic<ActivationRange> activationRange = mockStatic(ActivationRange.class);
            MockedStatic<TickThread> tickThread = mockStatic(TickThread.class)
        ) {
            activationRange.when(() -> ActivationRange.checkIfActive(entity)).thenReturn(true);
            tickThread.when(() -> TickThread.isTickThreadFor(entity)).thenReturn(ownsEntity);

            level.tickNonPassenger(entity);
        }
    }
}
