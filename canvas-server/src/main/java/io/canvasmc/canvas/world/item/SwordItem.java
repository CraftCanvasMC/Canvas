package io.canvasmc.canvas.world.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SwordItem extends Item {
    private static final BlocksAttacks BLOCKS_ATTACKS = new BlocksAttacks(
        0.0F, 0.0F,
        List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 0.5F)),
        new BlocksAttacks.ItemDamageFunction(Integer.MAX_VALUE, 0.0F, 1.0F),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    public SwordItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack itemInHand = player.getItemInHand(hand);
        final Consumable consumable = itemInHand.get(DataComponents.CONSUMABLE);

        if (consumable != null) {
            return consumable.startConsuming(player, itemInHand, hand);
        }

        final Equippable equippable = itemInHand.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.swappable()) {
            return equippable.swapWithEquipmentSlot(itemInHand, player);
        }

        final BlocksAttacks blocksAttacks = itemInHand.get(DataComponents.BLOCKS_ATTACKS);
        if (blocksAttacks != null) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }

        // we already know this is a sword, so we just apply the component for block attacks on this
        if (level.canvasConfig().combat.imitateSwordBlocking) {
            // this doesn't have block attacks. add it.
            itemInHand.set(DataComponents.BLOCKS_ATTACKS, BLOCKS_ATTACKS);
            player.inventoryMenu.broadcastChanges();
            player.startUsingItem(hand);
            player.canvas$isTemporarilyBlocking = true;
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean releaseUsing(final ItemStack stack, final Level level, final LivingEntity entity, final int timeLeft) {
        if (level.canvasConfig().combat.imitateSwordBlocking && entity instanceof Player player && player.canvas$isTemporarilyBlocking) {
            stack.remove(DataComponents.BLOCKS_ATTACKS);
            player.canvas$isTemporarilyBlocking = false;
        }
        return super.releaseUsing(stack, level, entity, timeLeft);
    }
}
