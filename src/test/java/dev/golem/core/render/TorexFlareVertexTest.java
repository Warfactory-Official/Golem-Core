package dev.golem.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TorexFlareVertexTest {

    @Test
    void adaptsOldFlareRowsToTheTargetsRenderType() {
        var vertex = CompatRenderTypes.torexFlareVertex(1F, 1F, 1F, 0.25F, 0x003C003C);
        //? if >=26.1 {
        assertEquals(1F, vertex.red());
        assertEquals(0.25F, vertex.alpha());
        assertEquals(0x003C003C, vertex.packedLight());
        //?} else {
        /*assertEquals(0.25F, vertex.red());
        assertEquals(1F, vertex.alpha());
        assertEquals(0x00F000F0, vertex.packedLight());
        *///?}
    }
}
