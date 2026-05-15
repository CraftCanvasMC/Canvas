package io.canvasmc.canvas.world.scores;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import java.util.List;
import java.util.Optional;

public class TeamData {
    private boolean allowFriendlyFire = true;
    private boolean seeFriendlyInvisibles = true;
    private Component displayName;
    private Component playerPrefix = CommonComponents.EMPTY;
    private Component playerSuffix = CommonComponents.EMPTY;
    private Team.Visibility nameTagVisibility = Team.Visibility.ALWAYS;
    private Team.Visibility deathMessageVisibility = Team.Visibility.ALWAYS;
    private ChatFormatting color = ChatFormatting.RESET;
    private Team.CollisionRule collisionRule = Team.CollisionRule.ALWAYS;

    public boolean isAllowFriendlyFire() {
        return allowFriendlyFire;
    }

    public TeamData setAllowFriendlyFire(final boolean allowFriendlyFire) {
        this.allowFriendlyFire = allowFriendlyFire;
        return this;
    }

    public boolean isSeeFriendlyInvisibles() {
        return seeFriendlyInvisibles;
    }

    public TeamData setSeeFriendlyInvisibles(final boolean seeFriendlyInvisibles) {
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        return this;
    }

    public Optional<ChatFormatting> getPackedColor() {
        return this.color != ChatFormatting.RESET ? Optional.of(this.color) : Optional.empty();
    }

    public ChatFormatting getColor() {
        return color;
    }

    public TeamData setColor(final ChatFormatting color) {
        this.color = color;
        return this;
    }

    public Team.CollisionRule getCollisionRule() {
        return collisionRule;
    }

    public TeamData setCollisionRule(final Team.CollisionRule collisionRule) {
        this.collisionRule = collisionRule;
        return this;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public TeamData setDisplayName(final Component displayName) {
        this.displayName = displayName;
        return this;
    }

    public Component getPlayerPrefix() {
        return playerPrefix;
    }

    public TeamData setPlayerPrefix(final Component playerPrefix) {
        this.playerPrefix = playerPrefix;
        return this;
    }

    public Component getPlayerSuffix() {
        return playerSuffix;
    }

    public TeamData setPlayerSuffix(final Component playerSuffix) {
        this.playerSuffix = playerSuffix;
        return this;
    }

    public Team.Visibility getDeathMessageVisibility() {
        return deathMessageVisibility;
    }

    public TeamData setDeathMessageVisibility(final Team.Visibility deathMessageVisibility) {
        this.deathMessageVisibility = deathMessageVisibility;
        return this;
    }

    public Team.Visibility getNameTagVisibility() {
        return nameTagVisibility;
    }

    public TeamData setNameTagVisibility(final Team.Visibility nameTagVisibility) {
        this.nameTagVisibility = nameTagVisibility;
        return this;
    }

    // the players list should always be a copy
    public PlayerTeam.Packed pack(final String name, final List<String> players) {
        return new PlayerTeam.Packed(
            name,
            Optional.of(this.displayName),
            this.color != ChatFormatting.RESET ? Optional.of(this.color) : Optional.empty(),
            this.allowFriendlyFire,
            this.seeFriendlyInvisibles,
            this.playerPrefix,
            this.playerSuffix,
            this.nameTagVisibility,
            this.deathMessageVisibility,
            this.collisionRule,
            players
        );
    }
}
