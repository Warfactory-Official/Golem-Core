// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.instance.Instance;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class BlockEntityVisual<T extends BlockEntity> implements Visual {

    protected final T blockEntity;
    protected final BlockPos pos;

    protected BlockEntityVisual(T blockEntity) {
        this.blockEntity = blockEntity;
        this.pos = blockEntity.getBlockPos();
    }

    public final T blockEntity() {
        return blockEntity;
    }

    public final BlockPos pos() {
        return pos;
    }

    // Hand out instances that should be re-rendered with the crumbling material when the block
    // is being broken. Default: no participation. Mirrors upstream Flywheel's API verbatim.
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
    }
}
//?}
