package dev.golem.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class OrderedShapeTest {

    private static final AABB FAR = new AABB(0, 0, 0, 1, 0.25D, 1);
    private static final AABB NEAR = new AABB(0, 0.75D, 0, 1, 1, 1);

    @Test
    void collisionIsTheUnion() {
        var shape = OrderedShape.of(new AABB(0, 0, 0, 1, 1, 1), FAR, NEAR);
        assertEquals(new AABB(0, 0, 0, 1, 1, 1), shape.bounds());
        assertEquals(2, shape.toAabbs().size());
        assertTrue(shape.collide(Direction.Axis.Y, new AABB(0.2D, 0.3D, 0.2D, 0.8D, 0.7D, 0.8D), -1D) > -0.06D,
                "a body between the slabs stops on the lower one");
    }

    @Test
    void picksTheFirstDeclaredBoxNotTheNearest() {
        var shape = OrderedShape.of(new AABB(0, 0, 0, 1, 1, 1), FAR, NEAR);
        var hit = shape.clip(new Vec3(0.5D, 2, 0.5D), new Vec3(0.5D, -1, 0.5D), BlockPos.ZERO);
        assertNotNull(hit);
        assertEquals(0.25D, hit.getLocation().y, 1e-9);
        assertEquals(Direction.UP, hit.getDirection());
        assertNull(shape.clip(new Vec3(2, 2, 2), new Vec3(3, 3, 3), BlockPos.ZERO));
    }

    @Test
    void movedShapeKeepsThePickAndNamesTheFootprintCell() {
        var shape = OrderedShape.of(new AABB(-1, 0, -1, 2, 4, 2), new AABB(-1, 0.75D, -1, 2, 1, 2));
        VoxelShape moved = shape.move(0, -2, 0);
        assertSame(OrderedShape.class, moved.getClass());
        assertSame(moved, moved.optimize());
        var queried = new BlockPos(10, 66, 10);
        var hit = moved.clip(new Vec3(10.5D, 67, 10.5D), new Vec3(10.5D, 64, 10.5D), queried);
        assertNotNull(hit);
        assertEquals(65D, hit.getLocation().y, 1e-9);
        assertEquals(new BlockPos(10, 65, 10), hit.getBlockPos(), "a top-face hit names the cell above the slab");
        var clamped = moved.clip(new Vec3(10.5D, 64.9D, 13), new Vec3(10.5D, 64.9D, 10.5D), queried);
        assertNotNull(clamped);
        assertEquals(new BlockPos(10, 64, 11), clamped.getBlockPos(), "a hit on the footprint's edge stays inside it");
    }
}
