package io.canvasmc.canvas.subcommands.regiondata;

import com.mojang.datafixers.util.Either;
import io.canvasmc.canvas.util.Util;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import java.util.Locale;
import java.util.function.ToIntFunction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.jspecify.annotations.Nullable;

public class MobCapUtils {

    private static final TextColor HEADER = TextColor.color(0xE066FF);
    private static final TextColor ACCENT = TextColor.color(0xFFA8ED);
    private static final TextColor LABEL = TextColor.color(0xD880FF);
    private static final TextColor SEPARATOR = TextColor.color(0x9B4DCC);
    private static final TextColor DEBUG = TextColor.color(0xE4B6F5);
    private static final TextColor DASH = TextColor.color(0xFF8AE8);
    private static final TextColor VALUE = TextColor.color(0xFFFFFF);

    private static final String SEP_LINE = "-----------------------";

    protected static void sendMobCaps(
        final CommandSourceStack css, final Either<ServerPlayer, RegionizedWorldData> target
    ) {
        final String tag;
        final ToIntFunction<MobCategory> countGetter;
        final ToIntFunction<MobCategory> limitGetter;

        final ServerLevel level = css.getLevel();
        final ServerPlayer targetedPlayer = target.left().orElse(null);
        final CommandSender bukkit = css.getSender();

        final int spawnableChunks;
        final boolean spawnStateNull;

        if (targetedPlayer != null) {
            // per-player mobcaps
            tag = "PP";
            countGetter = category -> level.getChunkSource().chunkMap.getMobCountNear(targetedPlayer, category);
            limitGetter = category -> level.getWorld().getSpawnLimitUnsafe(CraftSpawnCategory.toBukkit(category));
            spawnableChunks = -1;
            spawnStateNull = false;
        }
        else {
            // generic/region mobcaps
            final RegionizedWorldData data = target.right().orElseThrow();
            final NaturalSpawner.@Nullable SpawnState state = data.lastSpawnState;
            final int chunks = state == null ? 0 : state.getSpawnableChunkCount();

            tag = "GMC";
            countGetter = category -> state == null ? 0 : state.getMobCategoryCounts().getOrDefault(category, 0);
            limitGetter = category -> NaturalSpawner.globalLimitForCategory(level, category, chunks);
            spawnableChunks = chunks;
            spawnStateNull = state == null;
        }

        bukkit.sendMessage(Component.text(SEP_LINE, SEPARATOR));
        bukkit.sendMessage(Component.text()
            .append(Component.text("> MobCaps ", HEADER))
            .append(Component.text("(", DEBUG))
            .append(Component.text(tag, ACCENT))
            .append(Component.text(")", DEBUG))
            .build());
        bukkit.sendMessage(Component.text()
            .append(Component.text("- ", DASH))
            .append(Component.text("Dimension: ", DEBUG))
            .append(Component.text(Util.getLevelName(level), VALUE))
            .build());

        if (targetedPlayer == null) {
            bukkit.sendMessage(Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text("Spawn state: ", DEBUG))
                .append(spawnStateNull
                    ? Component.text("NULL (no data yet)", NamedTextColor.DARK_GRAY)
                    : Component.text("PRESENT", NamedTextColor.GREEN))
                .build());
            bukkit.sendMessage(Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text("Spawnables: ", DEBUG))
                .append(Component.text(spawnableChunks, VALUE))
                .build());
        }
        else {
            bukkit.sendMessage(Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text("PlayerPos: ", DEBUG))
                .append(Component.text(
                    "[%.1f, %.1f, %.1f]".formatted(targetedPlayer.getX(), targetedPlayer.getY(), targetedPlayer.getZ()),
                    VALUE))
                .build());
            bukkit.sendMessage(Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text("ViewDist: ", DEBUG))
                .append(Component.text(targetedPlayer.getBukkitEntity().getSendViewDistance() - 1, VALUE))
                .build());

            final Component name = Component.text(targetedPlayer.getScoreboardName(), NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showEntity(HoverEvent.ShowEntity.showEntity(
                    Key.key("minecraft", "player"),
                    targetedPlayer.getUUID(),
                    Component.text(targetedPlayer.getScoreboardName())
                )));

            bukkit.sendMessage(Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text("Targeting: ", DEBUG))
                .append(name)
                // add space so this doesn't look weird
                .append(Component.text(" "))
                .append(Component.object(ObjectContents.playerHead(targetedPlayer.getUUID())))
                .build());
        }

        bukkit.sendMessage(Component.text(SEP_LINE, SEPARATOR));

        for (final MobCategory category : MobCategory.values()) {
            final int limit = limitGetter.applyAsInt(category);

            final ComponentBuilder<?, ?> line = Component.text()
                .append(Component.text("- ", DASH))
                .append(Component.text(category.getName().toUpperCase(Locale.ROOT), LABEL))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text("|", SEPARATOR))
                .append(Component.text(" ", NamedTextColor.GRAY));

            if (limit != -1) {
                final int count = countGetter.applyAsInt(category);
                final TextColor countColor = count >= limit ? NamedTextColor.RED : VALUE;
                line.append(Component.text(count, countColor))
                    .append(Component.text("/", SEPARATOR))
                    .append(Component.text(limit, ACCENT));
            }
            else {
                line.append(Component.text("N/A", NamedTextColor.DARK_GRAY));
            }

            bukkit.sendMessage(line.build());
        }

        bukkit.sendMessage(Component.text(SEP_LINE, SEPARATOR));
    }
}
