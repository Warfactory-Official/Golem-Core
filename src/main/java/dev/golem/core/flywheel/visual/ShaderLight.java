// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

public record ShaderLight(float x, float y, float z, float r, float g, float b, float radius) {

    public static ShaderLight of(float x, float y, float z, float r, float g, float b, float radius) {
        return new ShaderLight(x, y, z, r, g, b, radius);
    }
}
//?}
