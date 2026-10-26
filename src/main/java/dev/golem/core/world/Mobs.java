package dev.golem.core.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import net.minecraft.world.entity.animal.fox.Fox;
//?} else {
/*import net.minecraft.world.entity.animal.Fox;
*///?}

/**
 * Access to a mob's AI goals from outside its class.
 *
 * <p>{@code Mob.goalSelector} is protected in vanilla. NeoForge and Forge publicize it with their own
 * access transformers; Fabric does not, so Core widens it (golemcore*.accesswidener). No vanilla or
 * loader API adds a goal to an existing mob from outside, which is why a widener is used here.
 */
public final class Mobs {

    private Mobs() {}

    public static ItemStack usedItem(UseItemGoal<?> goal) {
        return goal.item;
    }

    public static void foxSleepCountdown(Goal goal, int countdown) {
        ((Fox.SleepGoal) goal).countdown = countdown;
    }

    public static boolean foxHasShelter(Goal goal) {
        return ((Fox.FoxBehaviorGoal) goal).hasShelter();
    }

    public static boolean foxAlertable(Goal goal) {
        return ((Fox.FoxBehaviorGoal) goal).alertable();
    }

    /** The mob's goal selector, for adding or inspecting goals on mobs this mod did not create. */
    public static GoalSelector goals(Mob mob) {
        return mob.goalSelector;
    }
}
