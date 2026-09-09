package dev.golem.core.flywheel.instance;

//? if neoforge && >=26.1 {

// Cross-package accessor for the package-private InstanceHandle bits. Visuals only ever see
// the Instance interface; engines occasionally need to look behind the curtain — e.g., the
// INDIRECT backend's crumbling pass needs to know which AbstractInstancer (and therefore
// which group SSBO) an Instance lives in plus its index inside that instancer, to compute
// the global SSBO slot the vertex shader will read via the visibility list.
//
// Kept package-private for InstanceHandle access; methods are public so callers in any
// package can use them. ClassCastException intentionally propagates if a caller hands us a
// non-InstanceHandle Instance — there's only one impl, so a different one is a bug.
public final class InstanceInternals {

    private InstanceInternals() {
    }

    public static AbstractInstancer instancerOf(Instance instance) {
        return ((InstanceHandle) instance).instancer();
    }

    public static int indexOf(Instance instance) {
        return ((InstanceHandle) instance).index();
    }
}
//?}
