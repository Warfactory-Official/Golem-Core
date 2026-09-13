package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.flywheel.instance.Instance;

// Visual-facing handle for one (mesh, material, texture) bucket. Implementations differ by
// backend: INSTANCING uses a UBO per Instancer, INDIRECT shares one SSBO across every Instancer
// in the same (material, texture) group. Visuals only ever touch allocate(); the rest of the
// per-bucket state lives behind the implementation.
public interface Instancer {

    Instance allocate();
}
//?}
