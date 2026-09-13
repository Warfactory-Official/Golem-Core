package dev.golem.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The cases here are the ones {@code ChunkMap.onFullChunkStatusChange} actually produces: a repeat is
 * a promotion past full or a demotion back down to it, a fall is the drop out of full, and forgetting
 * an owner is a level unloading.
 */
class RisingEdgesTest {

    private static final Object LEVEL = new Object();
    private static final Object OTHER = new Object();

    @Test
    void onlyTheFirstRiseIsAnEdge() {
        RisingEdges<Object> edges = new RisingEdges<>();
        assertTrue(edges.rise(LEVEL, 7L), "The first rise is the edge");
        assertFalse(edges.rise(LEVEL, 7L), "A repeat is not an edge");
        assertFalse(edges.rise(LEVEL, 7L), "Nor is a third report");
    }

    @Test
    void fallingReArmsTheEdge() {
        RisingEdges<Object> edges = new RisingEdges<>();
        edges.rise(LEVEL, 7L);
        edges.fall(LEVEL, 7L);
        assertTrue(edges.rise(LEVEL, 7L), "A rise after a fall is an edge again");
    }

    @Test
    void ownersDoNotSeeEachOther() {
        RisingEdges<Object> edges = new RisingEdges<>();
        assertTrue(edges.rise(LEVEL, 7L));
        assertTrue(edges.rise(OTHER, 7L), "The same id under another owner is its own edge");
        edges.fall(LEVEL, 7L);
        assertTrue(edges.isUp(OTHER, 7L), "One owner falling must not drop another's");
    }

    @Test
    void forgettingAnOwnerLeavesTheRestStanding() {
        RisingEdges<Object> edges = new RisingEdges<>();
        edges.rise(LEVEL, 7L);
        edges.rise(OTHER, 7L);
        edges.forget(LEVEL);
        assertFalse(edges.isUp(LEVEL, 7L), "A forgotten owner holds nothing");
        assertTrue(edges.isUp(OTHER, 7L), "and takes nobody else with it");
        assertTrue(edges.rise(LEVEL, 7L), "A forgotten id rises again");
    }

    @Test
    void aFallWithNothingUpIsHarmless() {
        RisingEdges<Object> edges = new RisingEdges<>();
        edges.fall(LEVEL, 7L);
        edges.rise(LEVEL, 7L);
        edges.fall(LEVEL, 9L);
        assertTrue(edges.isUp(LEVEL, 7L), "Falling an id that was never up must not drop one that is");
    }

    @Test
    void theTableEmptiesBackToNothing() {
        RisingEdges<Object> edges = new RisingEdges<>();
        for (long id = 0; id < 64; id++) {
            edges.rise(LEVEL, id);
            edges.rise(OTHER, id);
        }
        assertEquals(128, edges.size());
        for (long id = 0; id < 64; id++) edges.fall(LEVEL, id);
        edges.forget(OTHER);
        assertEquals(0, edges.size(), "Nothing may be retained once every id has gone down");
    }
}
