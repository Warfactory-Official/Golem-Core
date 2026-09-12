package dev.golem.core.dejson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blockstate half of the declaration vocabulary.
 *
 * <p>Every table here is transcribed from a file this mod already ships, not from memory, because
 * a rotation table that is wrong by one quarter turn is not a crash — it is a machine facing the
 * wrong way that nobody notices until someone plays with it.
 *
 * <p>The set is smaller than the shapes it covers, because the interesting cases are products:
 * a machine with {@code facing}, {@code lit} and {@code ext} is {@link #facing()} crossed with two
 * {@link #select} dispatches, and the sixteen-entry file that describes it today is one line here.
 * {@link #stairs()} and {@link #slab()} are the two that are genuinely irregular and stay tables.
 */
public final class Variants {

    static final String TOP_SUFFIX = "_top";
    static final String DOUBLE_SUFFIX = "_double";
    static final String INNER_SUFFIX = "_inner";
    static final String OUTER_SUFFIX = "_outer";

    /**
     * Horizontal facings in the order a block model authored facing north is rotated through.
     * Transcribed from {@code blockstates/crate_steel.json}.
     */
    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    /**
     * Stair facings in the order a stair model authored facing east is rotated through, which is a
     * different base direction from every other facing block. Transcribed from
     * {@code blockstates/brick_compound_stairs.json}.
     */
    private static final String[] STAIR_FACING = {"east", "south", "west", "north"};

    private Variants() {}

    /** One variant for every state; the shape of 308 of the 608 blockstates shipped. */
    public static Dispatch simple() {
        return of(Collections.emptySet(), Collections.singletonMap("", one(Placement.NONE)));
    }

    /**
     * Horizontal facing, rotating one model. Six-way facing is the opt-in rather than the default:
     * of the facing blockstates shipped, 114 are horizontal and 7 are six-way.
     */
    public static Dispatch facing() {
        return facing("facing");
    }

    public static Dispatch facing(String property) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        for (int quarter = 0; quarter < HORIZONTAL.length; quarter++) {
            placements.put(property + "=" + HORIZONTAL[quarter],
                    one(Placement.rotated(0, quarter * 90, false)));
        }
        return of(Collections.singleton(property), placements);
    }

    /** Six-way facing, with {@code down} and {@code up} pitched rather than yawed. */
    public static Dispatch facingAll() {
        return facingAll("facing");
    }

    public static Dispatch facingAll(String property) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        placements.put(property + "=down", one(Placement.rotated(90, 0, false)));
        placements.put(property + "=up", one(Placement.rotated(270, 0, false)));
        for (int quarter = 0; quarter < HORIZONTAL.length; quarter++) {
            placements.put(property + "=" + HORIZONTAL[quarter],
                    one(Placement.rotated(0, quarter * 90, false)));
        }
        return of(Collections.singleton(property), placements);
    }

    /**
     * Pillar orientation for a {@link Models#column} model. Transcribed from
     * {@code blockstates/waste_log.json}.
     */
    public static Dispatch axis() {
        return axis("axis");
    }

    public static Dispatch axis(String property) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        placements.put(property + "=y", one(Placement.NONE));
        placements.put(property + "=z", one(Placement.rotated(90, 0, false)));
        placements.put(property + "=x", one(Placement.rotated(90, 90, false)));
        return of(Collections.singleton(property), placements);
    }

    /** The three slab models emitted by {@link Models#slabAll}. */
    public static Dispatch slab() {
        return slab("type");
    }

    public static Dispatch slab(String property) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        placements.put(property + "=bottom", one(Placement.NONE));
        placements.put(property + "=top", one(Placement.model(TOP_SUFFIX)));
        placements.put(property + "=double", one(Placement.model(DOUBLE_SUFFIX)));
        return of(Collections.singleton(property), placements);
    }

    /**
     * The forty-entry stair table over {@code facing}, {@code half} and {@code shape}, transcribed
     * from {@code blockstates/brick_compound_stairs.json}.
     *
     * <p>The rule the file encodes, with {@code i} the index of the facing in
     * {@link #STAIR_FACING}: a straight or right-handed corner is {@code y = 90i} on the bottom
     * half; a left-handed corner is a further quarter turn back. The top half adds {@code x = 180},
     * which flips handedness, so there the right-handed corner gains the extra quarter turn and the
     * left-handed one loses it. {@code uvlock} follows rotation, which is what keeps the top and
     * side textures upright on a rotated stair.
     */
    public static Dispatch stairs() {
        return stairs("facing", "half", "shape");
    }

    public static Dispatch stairs(String facing, String half, String shape) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        for (int quarter = 0; quarter < STAIR_FACING.length; quarter++) {
            int base = quarter * 90;
            for (boolean top : new boolean[]{false, true}) {
                int x = top ? 180 : 0;
                put(placements, key(facing, STAIR_FACING[quarter], half, top ? "top" : "bottom",
                        shape, "straight"), "", x, base);
                for (String corner : new String[]{"inner", "outer"}) {
                    String suffix = "_" + corner;
                    int right = top ? (base + 90) % 360 : base;
                    int left = top ? base : (base + 270) % 360;
                    put(placements, key(facing, STAIR_FACING[quarter], half, top ? "top" : "bottom",
                            shape, corner + "_right"), suffix, x, right);
                    put(placements, key(facing, STAIR_FACING[quarter], half, top ? "top" : "bottom",
                            shape, corner + "_left"), suffix, x, left);
                }
            }
        }
        return of(new LinkedHashSet<>(List.of(facing, half, shape)), placements);
    }

    /**
     * A property that changes nothing. Every value maps to the same model, which is how a block
     * whose state drives behaviour rather than appearance is written.
     */
    public static Dispatch uniform(String property, String... values) {
        if (values.length == 0) {
            throw new DejsonException("uniform('" + property + "') needs at least one value");
        }
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        for (String value : values) {
            placements.put(property + "=" + value, one(Placement.NONE));
        }
        return of(Collections.singleton(property), placements);
    }

    public static Dispatch uniformRange(String property, int from, int to) {
        return uniform(property, range(property, from, to));
    }

    /**
     * A property whose values pick a model by name suffix, given as alternating value and suffix.
     * {@code select("lit", "false", "_off", "true", "_on")} is the on/off pair, and crossing two of
     * these with {@link #facing()} is the sixteen-variant machine.
     */
    public static Dispatch select(String property, String... valueThenSuffix) {
        if (valueThenSuffix.length == 0 || valueThenSuffix.length % 2 != 0) {
            throw new DejsonException("select('" + property
                    + "') takes alternating value and suffix, got " + valueThenSuffix.length + " arguments");
        }
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        for (int index = 0; index < valueThenSuffix.length; index += 2) {
            placements.put(property + "=" + valueThenSuffix[index],
                    one(Placement.model(valueThenSuffix[index + 1])));
        }
        return of(Collections.singleton(property), placements);
    }

    /** A boolean property picking between {@code <base>} and {@code <base><suffix>}. */
    public static Dispatch flag(String property, String trueSuffix) {
        return select(property, "false", "", "true", trueSuffix);
    }

    /** An integer property picking {@code <base>_<value>} across an inclusive range. */
    public static Dispatch intRange(String property, int from, int to) {
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        for (String value : range(property, from, to)) {
            placements.put(property + "=" + value, one(Placement.model("_" + value)));
        }
        return of(Collections.singleton(property), placements);
    }

    /**
     * A random pool of {@code <base>_0} through {@code <base>_(count-1)}, selected per position by
     * the renderer. Crossed with a {@link #uniformRange} it is the shaded-ore shape: a property the
     * model ignores entirely, over a pool the renderer picks from.
     */
    public static Dispatch pool(int count) {
        if (count < 2) {
            throw new DejsonException("pool(" + count + ") needs at least two models");
        }
        List<Placement> placements = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            placements.add(Placement.model("_" + index));
        }
        return of(Collections.emptySet(),
                Collections.singletonMap("", Collections.unmodifiableList(placements)));
    }

    /**
     * Crosses dispatches, joining their keys and composing their placements. Key order within a
     * combined key follows argument order; vanilla matches keys by name, so it only decides how the
     * result reads.
     */
    public static Dispatch product(Dispatch... dispatches) {
        if (dispatches.length == 0) {
            throw new DejsonException("product() needs at least one dispatch");
        }
        Dispatch combined = dispatches[0];
        for (int index = 1; index < dispatches.length; index++) {
            combined = cross(combined, dispatches[index]);
        }
        return combined;
    }

    private static Dispatch cross(Dispatch left, Dispatch right) {
        Set<String> shared = new LinkedHashSet<>(left.properties());
        shared.retainAll(right.properties());
        if (!shared.isEmpty()) {
            throw new DejsonException("product() crosses two dispatches that both name " + shared);
        }
        Map<String, List<Placement>> placements = new LinkedHashMap<>();
        left.placements().forEach((leftKey, leftEntries) ->
                right.placements().forEach((rightKey, rightEntries) -> {
                    List<Placement> merged = new ArrayList<>(leftEntries.size() * rightEntries.size());
                    for (Placement leftEntry : leftEntries) {
                        for (Placement rightEntry : rightEntries) {
                            merged.add(leftEntry.plus(rightEntry));
                        }
                    }
                    placements.put(joinKeys(leftKey, rightKey), Collections.unmodifiableList(merged));
                }));
        Set<String> properties = new LinkedHashSet<>(left.properties());
        properties.addAll(right.properties());
        return of(properties, placements);
    }

    /** Wraps an already-built table; the escape hatch below the named presets. */
    public static Dispatch of(Set<String> properties, Map<String, List<Placement>> placements) {
        Set<String> frozenProperties = Collections.unmodifiableSet(new LinkedHashSet<>(properties));
        Map<String, List<Placement>> frozenPlacements =
                Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        if (frozenPlacements.isEmpty()) {
            throw new DejsonException("a dispatch with no variants would leave every state unmodelled");
        }
        return new Dispatch() {
            @Override public Map<String, List<Placement>> placements() { return frozenPlacements; }
            @Override public Set<String> properties() { return frozenProperties; }
        };
    }

    private static String joinKeys(String left, String right) {
        if (left.isEmpty()) {
            return right;
        }
        return right.isEmpty() ? left : left + "," + right;
    }

    private static String key(String a, String av, String b, String bv, String c, String cv) {
        return a + "=" + av + "," + b + "=" + bv + "," + c + "=" + cv;
    }

    private static void put(Map<String, List<Placement>> placements, String key,
                            String suffix, int x, int y) {
        placements.put(key, one(new Placement(suffix, x, y, x != 0 || y != 0)));
    }

    private static String[] range(String property, int from, int to) {
        if (to < from) {
            throw new DejsonException("range for '" + property + "' runs backwards: " + from + ".." + to);
        }
        String[] values = new String[to - from + 1];
        for (int index = 0; index < values.length; index++) {
            values[index] = Integer.toString(from + index);
        }
        return values;
    }

    private static List<Placement> one(Placement placement) {
        return Collections.singletonList(placement);
    }
}
