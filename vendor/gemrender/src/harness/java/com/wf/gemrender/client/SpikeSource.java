package com.wf.gemrender.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public interface SpikeSource {

    void ok(Component message);

    void fail(Component message);

    Vec3 position();
}
