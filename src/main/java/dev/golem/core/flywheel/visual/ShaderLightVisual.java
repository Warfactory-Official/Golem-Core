// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

// Implemented by visuals that contribute dynamic point lights to the scene (reactor cores,
// missile exhausts, explosion sources). collectLights is invoked each frame; emit 0..N lights
// via the collector. The renderer may impose a hard per-frame budget and cull distant ones.
public interface ShaderLightVisual {

    void collectLights(LightCollector collector);

    @FunctionalInterface
    interface LightCollector {
        void accept(ShaderLight light);
    }
}
//?}
