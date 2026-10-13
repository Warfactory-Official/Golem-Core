package dev.golem.core.client;

import net.minecraft.client.player.AbstractClientPlayer;

/**
 * The walk-bob values a first-person view sways by.
 *
 * <p>26.1 moved both onto {@code ClientAvatarState} behind interpolating accessors
 * ({@code getBackwardsInterpolatedWalkDistance} / {@code getInterpolatedBob}); before that they were the
 * public {@code walkDist}/{@code walkDistO} and {@code bob}/{@code oBob} fields, interpolated by the
 * caller. The 26.1 walk-distance accessor already negates, which is why the older branch does the same
 * by hand rather than leaving that to the consumer.
 *
 * <p>Client-only.
 */
public final class ViewBob {

    private ViewBob() {}

    /** How far the player has walked, interpolated and negated. This is the phase input for the sway. */
    public static float walkDistance(AbstractClientPlayer player, float partialTick) {
        //? if >=26.1 {
        return player.avatarState().getBackwardsInterpolatedWalkDistance(partialTick);
        //?} else {
        /*float walked = player.walkDist - player.walkDistO;
        return -(player.walkDist + walked * partialTick);
        *///?}
    }

    /** The bob amplitude, interpolated. */
    public static float bob(AbstractClientPlayer player, float partialTick) {
        //? if >=26.1 {
        return player.avatarState().getInterpolatedBob(partialTick);
        //?} else {
        /*return net.minecraft.util.Mth.lerp(partialTick, player.oBob, player.bob);
        *///?}
    }
}
