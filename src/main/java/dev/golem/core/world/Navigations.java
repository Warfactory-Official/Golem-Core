package dev.golem.core.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * {@code PathNavigation.moveTo}, reached through Core so a consumer's Stonecutter {@code snapTo}/{@code moveTo}
 * entity-position rename cannot rewrite the navigation call, whose name never changed.
 */
public final class Navigations {

    private Navigations() {}

    public static boolean navigateTo(PathNavigation navigation, double x, double y, double z, double speed) {
        return navigation.moveTo(x, y, z, speed);
    }

    public static boolean navigateTo(PathNavigation navigation, Entity target, double speed) {
        return navigation.moveTo(target, speed);
    }

    public static boolean navigateTo(PathNavigation navigation, @Nullable Path path, double speed) {
        return navigation.moveTo(path, speed);
    }

    /** {@code setCanOpenDoors}, on the ground navigation only before 26.1 lifted it to the base class. */
    public static void setCanOpenDoors(PathNavigation navigation, boolean canOpenDoors) {
        //? if >=26.1 {
        navigation.setCanOpenDoors(canOpenDoors);
        //?} else {
        /*if (navigation instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
            ground.setCanOpenDoors(canOpenDoors);
        }
        *///?}
    }
}
