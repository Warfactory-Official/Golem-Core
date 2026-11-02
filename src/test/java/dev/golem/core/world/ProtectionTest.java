package dev.golem.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.golem.core.world.Protection.Cause;
import dev.golem.core.world.Protection.Coverage;
import dev.golem.core.world.Protection.Gate;
import dev.golem.core.world.Protection.Provider;
import dev.golem.core.world.Protection.Source;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ProtectionTest {

    private static final long[] TIME = {0};

    private static Gate gate(Provider... providers) {
        return new Gate(null, Source.NONE, Cause.EXPLOSION, List.of(providers), () -> TIME[0]);
    }

    @Test
    void openGateAllowsEverything() {
        assertTrue(Gate.OPEN.isOpen());
        assertTrue(Gate.OPEN.mayAlter(1_000_000, 5, -1_000_000));
    }

    @Test
    void closedChunkDeniesWithoutPerPositionCalls() {
        int[] perBlock = {0};
        Gate gate = gate(new Provider() {
            @Override
            public Coverage coverage(Level level, int cx, int cz, Source source, Cause cause) {
                return cx == 1 ? Coverage.CLOSED : Coverage.OPEN;
            }

            @Override
            public boolean mayAlter(Level level, BlockPos pos, Source source, Cause cause) {
                perBlock[0]++;
                return true;
            }
        });
        assertFalse(gate.mayAlter(16, 0, 0));
        assertTrue(gate.mayAlter(0, 0, 0));
        assertEquals(0, perBlock[0]);
    }

    @Test
    void coverageCachedPerChunk() {
        int[] calls = {0};
        Gate gate = gate(new Provider() {
            @Override
            public Coverage coverage(Level level, int cx, int cz, Source source, Cause cause) {
                calls[0]++;
                return Coverage.OPEN;
            }
        });
        for (int i = 0; i < 100; i++) gate.mayAlter(i % 16, 3, 0);
        gate.mayAlter(16, 3, 0);
        gate.mayAlter(0, 3, 0);
        assertEquals(2, calls[0]);
    }

    @Test
    void cacheExpiresWithGameTick() {
        int[] calls = {0};
        Gate gate = gate(new Provider() {
            @Override
            public Coverage coverage(Level level, int cx, int cz, Source source, Cause cause) {
                calls[0]++;
                return Coverage.OPEN;
            }
        });
        TIME[0] = 10;
        gate.mayAlter(0, 0, 0);
        gate.mayAlter(1, 0, 0);
        TIME[0] = 11;
        gate.mayAlter(0, 0, 0);
        assertEquals(2, calls[0]);
    }

    @Test
    void partialAsksPerPositionAndDenyWins() {
        Provider allowAll = new Provider() {};
        Provider denyY = new Provider() {
            @Override
            public boolean mayAlter(Level level, BlockPos pos, Source source, Cause cause) {
                return pos.getY() != 7;
            }
        };
        Gate gate = gate(allowAll, denyY);
        assertTrue(gate.mayAlter(0, 6, 0));
        assertFalse(gate.mayAlter(0, 7, 0));
    }

    @Test
    void removeDeniedFiltersInPlace() {
        Gate gate = gate(new Provider() {
            @Override
            public boolean mayAlter(Level level, BlockPos pos, Source source, Cause cause) {
                return pos.getX() >= 0;
            }
        });
        var positions = new ArrayList<>(List.of(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0)));
        assertTrue(gate.removeDenied(positions));
        assertEquals(List.of(new BlockPos(1, 0, 0)), positions);
    }

    @Test
    void singleShotHonoursChunkCoverage() {
        Provider closesChunkOne = new Provider() {
            @Override
            public Coverage coverage(Level level, int cx, int cz, Source source, Cause cause) {
                return cx == 1 ? Coverage.CLOSED : Coverage.OPEN;
            }
        };
        Protection.register(closesChunkOne);
        try {
            assertFalse(Protection.mayAlter(null, new BlockPos(16, 0, 0), Source.NONE, Cause.EXPLOSION));
            assertTrue(Protection.mayAlter(null, new BlockPos(0, 0, 0), Source.NONE, Cause.EXPLOSION));
        } finally {
            Protection.unregister(closesChunkOne);
        }
    }
}
