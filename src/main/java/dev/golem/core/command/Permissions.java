package dev.golem.core.command;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;

/**
 * Command permission predicates across the command-source API change.
 *
 * <p>26.1 replaced the integer {@code hasPermission(int)} on the command source with typed
 * {@code PermissionCheck}s. The integer levels still exist underneath ({@code PermissionLevel.byId}), so a
 * consumer that thinks in the classic 0-4 levels can keep doing so here.
 */
public final class Permissions {

    private Permissions() {}

    /** Level 2, the vanilla default for cheat commands. */
    public static Predicate<CommandSourceStack> gamemasters() {
        return level(2);
    }

    /** No requirement: every command source, including a level-0 player. */
    public static Predicate<CommandSourceStack> any() {
        return source -> true;
    }

    /**
     * The classic integer permission level: 0 everyone, 1 moderators, 2 game masters, 3 admins, 4 owners.
     * Singleplayer owners with cheats enabled hold level 4.
     */
    public static Predicate<CommandSourceStack> level(int level) {
        if (level <= 0) return any();
        //? if >=26.1 {
        return net.minecraft.commands.Commands.hasPermission(new net.minecraft.server.permissions.PermissionCheck.Require(
                new net.minecraft.server.permissions.Permission.HasCommandLevel(
                        net.minecraft.server.permissions.PermissionLevel.byId(Math.min(level, 4)))));
        //?} else {
        /*int required = Math.min(level, 4);
        return source -> source.hasPermission(required);
        *///?}
    }
}
