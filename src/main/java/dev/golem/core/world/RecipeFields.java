package dev.golem.core.world;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * The fields of a {@link RecipeTables.Table}'s row JSON, described for tools that write rows by hand: Golem Core's
 * scripting adapter turns them into a KubeJS recipe schema ({@code event.recipes.hbm.press(output, input, stamp)}), on
 * every node KubeJS exists for. The table's codec stays the only parser; this is a description of its JSON keys, so
 * whatever a script writes is checked by the same codec as a datapack file.
 *
 * <p>Every table has fields: a table that states none gets one {@link Kind#JSON} field per key of its codec
 * ({@code MapCodec.keys}), so scripts can always write {@code event.recipes.<ns>.<table>({...})}. Stating them gives
 * typed arguments (an item, a tag, {@code '2x minecraft:iron_ingot'}, a fluid) and lets scripts find rows by input and
 * output ({@code event.remove({output: 'hbm:ingot_steel'})}).
 *
 * <p>Typed values reach the row through a {@link Shape}: the adapter hands the shape a neutral value ({@link ItemIn},
 * {@link ItemOut}, {@link FluidAmount}, or a plain number/string/boolean) and writes the JSON it returns. A script may
 * always pass a plain object instead ({@code {material: 'iron', shape: 'ingot'}}): it is written unchanged, so a
 * table's own special forms stay writable. A field with no shape uses the kind's default (vanilla's ingredient and
 * item-stack JSON for this node, {@code {"fluid", "amount"}} for fluids).
 *
 * <pre>{@code
 * table.fields(RecipeFields.of()
 *     .add(RecipeFields.field("output", Kind.ITEM_OUT).shape(MY_OUTPUT))
 *     .add(RecipeFields.field("input", Kind.ITEM_IN).list())
 *     .add(RecipeFields.field("duration", Kind.INT).optional(new JsonPrimitive(100))));
 * }</pre>
 *
 * <p>The order of the fields is the order of the script function's positional arguments; optional fields come last.
 */
public final class RecipeFields {

    /** What a field holds. */
    public enum Kind {
        /** An item input: one item or tag, with a count ({@link ItemIn}). */
        ITEM_IN,
        /** An item output: one item with a count and an optional chance ({@link ItemOut}). */
        ITEM_OUT,
        /** A fluid input, in millibuckets ({@link FluidAmount}). */
        FLUID_IN,
        /** A fluid output, in millibuckets ({@link FluidAmount}). */
        FLUID_OUT,
        INT,
        LONG,
        DOUBLE,
        BOOLEAN,
        STRING,
        /** A namespaced id ({@code "hbm:iron"}), as a string. */
        ID,
        /** Any JSON, written unchanged. */
        JSON
    }

    /** What the field is to a script's filters ({@code input:}, {@code output:}). */
    public enum Role {
        INPUT,
        OUTPUT,
        OTHER;

        static Role of(Kind kind) {
            return switch (kind) {
                case ITEM_IN, FLUID_IN -> INPUT;
                case ITEM_OUT, FLUID_OUT -> OUTPUT;
                default -> OTHER;
            };
        }
    }

    /** An item input: {@code id} is an item id, or a tag id when {@code tag}. */
    public record ItemIn(String id, boolean tag, int count) {}

    /** An item output; {@code chance} is null when the script gave none. */
    public record ItemOut(String id, int count, @Nullable Float chance) {}

    /** A fluid by its vanilla registry id and an amount in millibuckets (never loader droplets). */
    public record FluidAmount(String id, int amount) {}

    /**
     * How a typed value becomes the row's JSON for this field, and back. {@link #read} lets scripts filter and replace
     * by item or fluid; returning null there means "not representable" (the script then sees the raw JSON).
     */
    public interface Shape {
        JsonElement write(Object value);

        @Nullable
        Object read(JsonElement json);
    }

    /** One field: its JSON key, kind, and how scripts pass it. Immutable; the builder methods return copies. */
    public record Field(String name, Kind kind, Role role, boolean isList, boolean isOptional,
                        @Nullable JsonElement fallback, @Nullable Shape shape, List<String> aliases) {

        public Field {
            Objects.requireNonNull(name);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(role);
            aliases = List.copyOf(aliases);
        }

        /** A list of values (a JSON array). */
        public Field list() {
            return new Field(name, kind, role, true, isOptional, fallback, shape, aliases);
        }

        /** May be left out; the codec's default applies. */
        public Field optional() {
            return new Field(name, kind, role, isList, true, fallback, shape, aliases);
        }

        /** May be left out; scripts that leave it out write {@code fallback}. */
        public Field optional(JsonElement fallback) {
            return new Field(name, kind, role, isList, true, fallback, shape, aliases);
        }

        public Field shape(Shape shape) {
            return new Field(name, kind, role, isList, isOptional, fallback, shape, aliases);
        }

        public Field role(Role role) {
            return new Field(name, kind, role, isList, isOptional, fallback, shape, aliases);
        }

        /** Other names a script may use for this field (KubeJS builder methods, e.g. {@code .energy(…)}). */
        public Field alias(String... names) {
            List<String> all = new ArrayList<>(aliases);
            all.addAll(List.of(names));
            return new Field(name, kind, role, isList, isOptional, fallback, shape, all);
        }
    }

    public static Field field(String name, Kind kind) {
        return new Field(name, kind, Role.of(kind), false, false, null, null, List.of());
    }

    public static RecipeFields of() {
        return new RecipeFields(List.of());
    }

    private final List<Field> fields;

    private RecipeFields(List<Field> fields) {
        this.fields = List.copyOf(fields);
    }

    /** A copy with {@code field} appended; a field named twice is rejected. */
    public RecipeFields add(Field field) {
        for (Field existing : fields) {
            if (existing.name().equals(field.name())) throw new IllegalArgumentException("Field " + field.name() + " stated twice");
        }
        List<Field> all = new ArrayList<>(fields);
        all.add(field);
        return new RecipeFields(all);
    }

    /** A copy with every field of {@code more} appended. */
    public RecipeFields addAll(RecipeFields more) {
        RecipeFields result = this;
        for (Field field : more.fields) result = result.add(field);
        return result;
    }

    /** A copy without the named field (a subclass table that does not have a base field). */
    public RecipeFields without(String name) {
        List<Field> all = new ArrayList<>(fields);
        all.removeIf(field -> field.name().equals(name));
        return new RecipeFields(all);
    }

    public List<Field> fields() {
        return fields;
    }

    @Nullable
    public Field get(String name) {
        for (Field field : fields) if (field.name().equals(name)) return field;
        return null;
    }
}
