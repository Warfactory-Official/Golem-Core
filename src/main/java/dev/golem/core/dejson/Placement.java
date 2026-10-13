package dev.golem.core.dejson;

/**
 * One entry of a blockstate variant, before it is bound to a concrete model id.
 *
 * <p>Kept separate from {@link Variant} so dispatches can be composed: {@link Variants#product}
 * crosses a rotation dispatch with a model-selecting one, and that only works while the model is
 * still a suffix to concatenate rather than a finished id.
 *
 * <p>{@code uvlock} is carried rather than derived. It is not a function of the rotation: a facing
 * machine is rotated without it and a stair with it. The dispatch that knows which kind of shape
 * it is must say.
 */
public record Placement(String suffix, int x, int y, boolean uvlock) {

    public static final Placement NONE = new Placement("", 0, 0, false);

    public Placement {
        if (suffix == null) {
            throw new DejsonException("placement suffix is null");
        }
        Variant.requireQuadrant(x, "x");
        Variant.requireQuadrant(y, "y");
    }

    public static Placement rotated(int x, int y, boolean uvlock) {
        return new Placement("", x, y, uvlock);
    }

    public static Placement model(String suffix) {
        return new Placement(suffix, 0, 0, false);
    }

    /** Concatenates suffixes, adds rotations modulo a full turn, and keeps uvlock if either had it. */
    public Placement plus(Placement other) {
        return new Placement(suffix + other.suffix,
                (x + other.x) % 360,
                (y + other.y) % 360,
                uvlock || other.uvlock);
    }

    public Variant bind(String base) {
        return new Variant(base + suffix, x, y, uvlock);
    }
}
