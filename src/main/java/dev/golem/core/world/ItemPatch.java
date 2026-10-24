package dev.golem.core.world;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/**
 * The data a stack carries beyond its item and count, in this node's own form: a component patch from 1.20.5, the
 * stack's NBT below. A recipe row states it under {@link #KEY}, where vanilla (or Forge on 1.20.1) spells a result's
 * data. It holds no stack, so a row can be read before 26.1 binds item components.
 */
public final class ItemPatch {

    //? if >=1.20.5 {
    public static final String KEY = "components";
    public static final ItemPatch NONE = new ItemPatch(net.minecraft.core.component.DataComponentPatch.EMPTY);
    public static final Codec<ItemPatch> CODEC = net.minecraft.core.component.DataComponentPatch.CODEC
            .xmap(ItemPatch::new, patch -> patch.data);

    private final net.minecraft.core.component.DataComponentPatch data;

    private ItemPatch(net.minecraft.core.component.DataComponentPatch data) {
        this.data = data;
    }

    public static ItemPatch of(ItemStack stack) {
        return new ItemPatch(stack.getComponentsPatch());
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    /** Writes this data onto {@code stack} over what it holds, and returns it. */
    public ItemStack apply(ItemStack stack) {
        stack.applyComponents(data);
        return stack;
    }
    //?} else {
    /*public static final String KEY = "nbt";
    public static final ItemPatch NONE = new ItemPatch(new net.minecraft.nbt.CompoundTag());
    // SNBT keeps every tag type (a JSON object would turn an int array into a list); an object is read too.
    private static final Codec<net.minecraft.nbt.CompoundTag> SNBT = Codec.STRING.comapFlatMap(ItemPatch::parse,
            net.minecraft.nbt.CompoundTag::toString);
    public static final Codec<ItemPatch> CODEC = Codec.either(SNBT, net.minecraft.nbt.CompoundTag.CODEC).xmap(
            either -> new ItemPatch(either.map(tag -> tag, tag -> tag)),
            patch -> com.mojang.datafixers.util.Either.left(patch.data));

    private final net.minecraft.nbt.CompoundTag data;

    private ItemPatch(net.minecraft.nbt.CompoundTag data) {
        this.data = data;
    }

    private static com.mojang.serialization.DataResult<net.minecraft.nbt.CompoundTag> parse(String snbt) {
        try {
            return com.mojang.serialization.DataResult.success(net.minecraft.nbt.TagParser.parseTag(snbt));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return com.mojang.serialization.DataResult.error(() -> "not SNBT: " + e.getMessage());
        }
    }

    public static ItemPatch of(ItemStack stack) {
        return stack.getTag() == null ? NONE : new ItemPatch(stack.getTag().copy());
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    /^* Writes this data onto {@code stack} over what it holds, and returns it. ^/
    public ItemStack apply(ItemStack stack) {
        if (!data.isEmpty()) stack.getOrCreateTag().merge(data.copy());
        return stack;
    }
    *///?}

    /** This data as {@code ops} spells it under {@link #KEY}. */
    public JsonElement json(DynamicOps<JsonElement> ops) {
        return CODEC.encodeStart(ops, this).result()
                .orElseThrow(() -> new IllegalArgumentException("item data does not encode: " + this));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemPatch patch && data.equals(patch.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }

    @Override
    public String toString() {
        return String.valueOf(data);
    }
}
