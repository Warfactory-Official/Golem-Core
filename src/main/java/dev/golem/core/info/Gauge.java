package dev.golem.core.info;

import dev.golem.core.data.Tags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * One line of a block-info overlay such as Jade: {@code value} of {@code max} in {@code unit}, drawn as a bar filled
 * left to right with a ramp from {@code fromColor} to {@code toColor} (ARGB). A {@code max} of zero or less is a plain
 * text line. {@code label} is a translation key, or literal text where no key exists.
 */
public record Gauge(Kind kind, String label, long value, long max, String unit, int fromColor, int toColor) {

    /** An energy gauge replaces the overlay's own energy view (a machine also exposed as FE shows once). */
    public enum Kind { ENERGY, OTHER }

    public static Gauge energy(long value, long max, String unit, int fromColor, int toColor) {
        return new Gauge(Kind.ENERGY, "", value, max, unit, fromColor, toColor);
    }

    public static Gauge of(String label, long value, long max, String unit, int fromColor, int toColor) {
        return new Gauge(Kind.OTHER, label, value, max, unit, fromColor, toColor);
    }

    public static ListTag save(List<Gauge> gauges) {
        ListTag list = new ListTag();
        for (Gauge gauge : gauges) {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", gauge.kind.name());
            tag.putString("label", gauge.label);
            tag.putLong("value", gauge.value);
            tag.putLong("max", gauge.max);
            tag.putString("unit", gauge.unit);
            tag.putInt("from", gauge.fromColor);
            tag.putInt("to", gauge.toColor);
            list.add(tag);
        }
        return list;
    }

    public static List<Gauge> load(CompoundTag data, String key) {
        ListTag list = Tags.getListOrEmpty(data, key);
        List<Gauge> gauges = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = Tags.getCompoundOrEmpty(list, i);
            Kind kind = "ENERGY".equals(Tags.getStringOr(tag, "kind", "")) ? Kind.ENERGY : Kind.OTHER;
            gauges.add(new Gauge(kind, Tags.getStringOr(tag, "label", ""), Tags.getLongOr(tag, "value", 0L),
                    Tags.getLongOr(tag, "max", 0L), Tags.getStringOr(tag, "unit", ""),
                    Tags.getIntOr(tag, "from", -1), Tags.getIntOr(tag, "to", -1)));
        }
        return gauges;
    }

    /** The bar's colour at {@code position} (0 at the left edge, 1 at the right), each channel interpolated. */
    public int colorAt(float position) {
        int color = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int from = fromColor >>> shift & 0xFF;
            int to = toColor >>> shift & 0xFF;
            color |= Math.round(from + (to - from) * position) << shift;
        }
        return color;
    }
}
