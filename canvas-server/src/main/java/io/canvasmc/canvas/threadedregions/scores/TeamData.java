package io.canvasmc.canvas.threadedregions.scores;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
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
    private Optional<TeamColor> color = Optional.empty();
    private Team.CollisionRule collisionRule = Team.CollisionRule.ALWAYS;

    @Contract("_ -> new")
    public static @NonNull TeamData copyOf(final @NonNull TeamData toCopy) {
        return new TeamData(
            toCopy.isAllowFriendlyFire(),
            toCopy.isSeeFriendlyInvisibles(),
            toCopy.getDisplayName(),
            toCopy.getPlayerPrefix(),
            toCopy.getPlayerSuffix(),
            toCopy.getNameTagVisibility(),
            toCopy.getDeathMessageVisibility(),
            toCopy.getColor(),
            toCopy.getCollisionRule()
        );
    }

    public TeamData(
        final boolean allowFriendlyFire,
        final boolean seeFriendlyInvisibles,
        final Component displayName,
        final Component playerPrefix,
        final Component playerSuffix,
        final Team.Visibility nameTagVisibility,
        final Team.Visibility deathMessageVisibility,
        final Optional<TeamColor> color,
        final Team.CollisionRule collisionRule
    ) {
        this.allowFriendlyFire = allowFriendlyFire;
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        this.displayName = displayName;
        this.playerPrefix = playerPrefix;
        this.playerSuffix = playerSuffix;
        this.nameTagVisibility = nameTagVisibility;
        this.deathMessageVisibility = deathMessageVisibility;
        this.color = color;
        this.collisionRule = collisionRule;
    }

    public TeamData() {
        // no-op
    }

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

    public Optional<TeamColor> getColor() {
        return color;
    }

    public TeamData setColor(final Optional<TeamColor> color) {
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
            this.color,
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
