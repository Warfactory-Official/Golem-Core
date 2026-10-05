package dev.golem.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The staging every transactional backend shares: nothing reaches the machine before commit, every figure is
 * the machine's own (typed tanks, receiving/sending faces, rate limits), and an aborted transaction leaves no
 * trace.
 */
class FluidStagingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Tank 0: water, receives, 1,000 mB, 300 mB per call. Tank 1: lava, sends, 500 mB. */
    private static final class Machine implements FluidMachine {
        final Fluid[] types = {Fluids.WATER, Fluids.LAVA};
        final long[] fill = {200L, 400L};
        final long[] cap = {1_000L, 500L};
        final List<String> calls = new ArrayList<>();
        boolean valid = true;

        @Override public Object owner() { return this; }
        @Override public boolean valid() { return valid; }
        @Override public int tanks() { return 2; }
        @Override public Fluid fluid(int tank) { return fill[tank] > 0 ? types[tank] : Fluids.EMPTY; }
        @Override public long amount(int tank) { return fill[tank]; }
        @Override public long capacity(int tank) { return cap[tank]; }
        @Override public boolean accepts(int tank, Fluid fluid) { return tank == 0 && fluid == types[0]; }
        @Override public boolean provides(int tank, Fluid fluid) { return tank == 1 && fluid == types[1]; }
        @Override public long demand(Fluid fluid) { return fluid == Fluids.WATER ? Math.min(300L, cap[0] - fill[0]) : 0L; }
        @Override public long available(Fluid fluid) { return fluid == Fluids.LAVA ? fill[1] : 0L; }

        @Override
        public long receive(Fluid fluid, long mb) {
            calls.add("receive " + mb);
            fill[0] += mb;
            return mb;
        }

        @Override
        public long give(Fluid fluid, long mb) {
            calls.add("give " + mb);
            fill[1] -= mb;
            return mb;
        }
    }

    @Test
    void typedTanksTakeOnlyTheirFluidOnTheirRole() {
        Machine machine = new Machine();
        FluidStaging staging = new FluidStaging(2);
        assertEquals(300L, staging.insertable(machine, 0, Fluids.WATER), "rate limit below the room");
        assertEquals(0L, staging.insertable(machine, 0, Fluids.LAVA), "a water tank refuses lava");
        assertEquals(0L, staging.insertable(machine, 1, Fluids.LAVA), "a sending tank takes nothing");
        assertEquals(0L, staging.extractable(machine, 0, Fluids.WATER), "a receiving tank gives nothing");
        assertEquals(400L, staging.extractable(machine, 1, Fluids.LAVA));
        assertEquals(0L, staging.insertable(machine, 0, Fluids.EMPTY));
    }

    @Test
    void nothingMovesBeforeCommitAndTheRateIsShared() {
        Machine machine = new Machine();
        FluidStaging staging = new FluidStaging(2);
        assertEquals(200L, staging.stageInsert(machine, 0, Fluids.WATER, 200L));
        assertEquals(100L, staging.stageInsert(machine, 0, Fluids.WATER, 200L), "the second call gets what is left of 300");
        assertEquals(0L, staging.stageInsert(machine, 0, Fluids.WATER, 200L));
        assertEquals(200L, machine.fill[0], "the machine is untouched while staged");
        assertEquals(500L, staging.amount(machine, 0), "a peer reads the staged amount");
        assertEquals(List.of(), machine.calls);

        staging.commit();
        assertEquals(List.of("receive 300"), machine.calls, "one entry-point call with the total");
        assertEquals(500L, machine.fill[0]);
        assertEquals(500L, staging.amount(machine, 0), "nothing left staged after commit");
    }

    @Test
    void anAbortedTransactionLeavesNoTrace() {
        Machine machine = new Machine();
        FluidStaging staging = new FluidStaging(2);
        FluidStaging.Saved before = staging.save();
        staging.stageInsert(machine, 0, Fluids.WATER, 250L);
        staging.stageExtract(machine, 1, Fluids.LAVA, 150L);
        assertEquals(250L, staging.amount(machine, 1));
        staging.restore(before);
        assertEquals(200L, staging.amount(machine, 0));
        assertEquals(400L, staging.amount(machine, 1));
        staging.commit();
        assertEquals(List.of(), machine.calls, "a rolled-back crossing is never applied");
    }

    @Test
    void extractCommitsThroughTheExitPoint() {
        Machine machine = new Machine();
        FluidStaging staging = new FluidStaging(2);
        assertEquals(150L, staging.stageExtractAny(machine, Fluids.LAVA, 150L));
        assertSame(Fluids.LAVA, staging.fluid(machine, 1));
        staging.commit();
        assertEquals(List.of("give 150"), machine.calls);
        assertEquals(250L, machine.fill[1]);
    }

    @Test
    void anInvalidMachineAnswersNothing() {
        Machine machine = new Machine();
        machine.valid = false;
        FluidStaging staging = new FluidStaging(2);
        assertEquals(0L, staging.stageInsert(machine, 0, Fluids.WATER, 100L));
        assertEquals(0L, FluidStaging.insertNow(machine, Fluids.WATER, 100L, false));
    }

    @Test
    void simulateFlagPathMovesAtOnceOnlyOnExecute() {
        Machine machine = new Machine();
        assertEquals(300L, FluidStaging.insertNow(machine, Fluids.WATER, 1_000L, true));
        assertEquals(List.of(), machine.calls, "a simulation changes nothing");
        assertEquals(300L, FluidStaging.insertNow(machine, Fluids.WATER, 1_000L, false));
        assertEquals(List.of("receive 300"), machine.calls);
        assertEquals(0L, FluidStaging.insertNow(machine, Fluids.LAVA, 1_000L, false));
        assertEquals(400L, FluidStaging.extractNow(machine, Fluids.LAVA, 1_000L, false));
        assertEquals(0L, machine.fill[1]);
    }
}
