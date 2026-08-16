package dev.golem.core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

//? if <26.1 {
/*import net.minecraft.gametest.framework.GameTestAssertException;
*///?}

/**
 * {@code GameTestHelper} calls whose shape changed between the two test frameworks.
 *
 * <p>Most of the helper is identical across the eras, which is why the test bodies port unchanged; this
 * covers the handful that are not.
 */
public final class GameTestHelpers {

    private GameTestHelpers() {}

    /**
     * The block entity at a relative position, asserted to be of the given type.
     *
     * <p>26.1 takes the expected class and does the assertion itself. Older versions return a bare
     * {@code BlockEntity}, so the check is done here — and it must fail the test rather than throw a
     * {@code ClassCastException}, which the framework would report as an error instead of a failure.
     */
    public static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        //? if >=26.1 {
        return helper.getBlockEntity(pos, type);
        //?} else {
        /*BlockEntity be = helper.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            throw new GameTestAssertException("Expected " + type.getSimpleName() + " at " + pos
                    + " but found " + (be == null ? "nothing" : be.getClass().getSimpleName()));
        }
        return type.cast(be);
        *///?}
    }

    /** Creates the test player despite the GameTest helper's era-specific overload. */
    public static Player mockPlayer(GameTestHelper helper, GameType gameType) {
        //? if >=1.21 {
        return helper.makeMockPlayer(gameType);
        //?} else {
        /*return helper.makeMockPlayer();
        *///?}
    }
}
