package dev.golem.core.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

/**
 * Gzip-compressed NBT files (structure files, 1.7 {@code CompressedStreamTools}) and the untyped list reads
 * such files need, across the accounter parameter added in 1.20.2 and the 26.1 typed-getter rewrite.
 */
public final class NbtFiles {

    private NbtFiles() {}

    /** Reads a whole compressed compound with no size quota (large hand-made structure files exceed it). */
    public static CompoundTag readCompressed(InputStream in) throws IOException {
        //? if >=1.20.2 {
        return NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        //?} else {
        /*return NbtIo.readCompressed(in);
        *///?}
    }

    public static void writeCompressed(CompoundTag tag, OutputStream out) throws IOException {
        NbtIo.writeCompressed(tag, out);
    }

    /** The list stored under {@code name} whatever its element type; empty when missing or not a list. */
    public static ListTag list(CompoundTag tag, String name) {
        return tag.get(name) instanceof ListTag list ? list : new ListTag();
    }

    /** The integer value of element {@code index} of a numeric list, 0 for anything else. */
    public static int intAt(ListTag list, int index) {
        if (index < 0 || index >= list.size()) return 0;
        Tag element = list.get(index);
        if (!(element instanceof NumericTag number)) return 0;
        //? if >=26.1 {
        return number.intValue();
        //?} else {
        /*return number.getAsInt();
        *///?}
    }

    /** The text of element {@code index} of a string list, "" for anything else. */
    public static String stringAt(ListTag list, int index) {
        if (index < 0 || index >= list.size()) return "";
        Tag element = list.get(index);
        if (!(element instanceof net.minecraft.nbt.StringTag string)) return "";
        //? if >=26.1 {
        return string.value();
        //?} else {
        /*return string.getAsString();
        *///?}
    }

    /** The compound stored under {@code name}, or null when missing or of another type. */
    public static CompoundTag compoundOrNull(CompoundTag tag, String name) {
        return tag.get(name) instanceof CompoundTag compound ? compound : null;
    }
}
