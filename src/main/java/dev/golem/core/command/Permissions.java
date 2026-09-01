package dev.golem.core.command;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;

/** Command permission predicates across the command-source API change. */
public final class Permissions {

    private Permissions() {}

    public static Predicate<CommandSourceStack> gamemasters() {
        //? if >=26.1 {
        return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS);
        *///?}
    }
}
