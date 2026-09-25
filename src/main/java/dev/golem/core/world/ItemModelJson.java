package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Item model JSON in the node's own format, for items minted at runtime where no build step converts a
 * 1.21.4+ item definition downward. {@code out} takes a path relative to the namespace root, without
 * {@code .json}.
 */
public final class ItemModelJson {

    private ItemModelJson() {}

    /** {@code models/item/<path>}: one sprite on {@code minecraft:item/generated}. */
    public static JsonObject generated(String sprite) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", sprite);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", textures);
        return model;
    }

    /**
     * Variant {@code n} draws {@code models.get(n)}; the first is the fallback and names the item's own model,
     * written from {@code ownModel}. 1.21.4+: plus a {@code minecraft:select} definition on {@code property}.
     * Older: {@code ownModel} carries ascending {@code overrides} on the same numeric property.
     */
    public static void variantSelect(BiConsumer<String, JsonElement> out, String itemPath, String property,
                                     JsonObject ownModel, List<String> models) {
        JsonObject model = ownModel.deepCopy();
        //? if >=1.21.4 {
        JsonArray cases = new JsonArray();
        for (int variant = 0; variant < models.size(); variant++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("when", String.valueOf(variant));
            entry.add("model", reference(models.get(variant)));
            cases.add(entry);
        }
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", property);
        select.add("cases", cases);
        select.add("fallback", reference(models.get(0)));
        JsonObject definition = new JsonObject();
        definition.add("model", select);
        out.accept("items/" + itemPath, definition);
        //?} else {
        /*JsonArray overrides = new JsonArray();
        for (int variant = 0; variant < models.size(); variant++) {
            JsonObject predicate = new JsonObject();
            predicate.addProperty(property, variant);
            JsonObject entry = new JsonObject();
            entry.add("predicate", predicate);
            entry.addProperty("model", models.get(variant));
            overrides.add(entry);
        }
        model.add("overrides", overrides);
        *///?}
        out.accept("models/item/" + itemPath, model);
    }

    /**
     * {@code items/<path>} naming {@code model} on every node, read natively from 1.21.4 and by a mod's own
     * reader before it. Older nodes also get {@code models/item/<path>} parented to {@code legacyParent}.
     */
    public static void definition(BiConsumer<String, JsonElement> out, String itemPath, JsonObject model,
                                  String legacyParent) {
        JsonObject definition = new JsonObject();
        definition.add("model", model);
        out.accept("items/" + itemPath, definition);
        //? if <1.21.4 {
        /*JsonObject legacy = new JsonObject();
        legacy.addProperty("parent", legacyParent);
        out.accept("models/item/" + itemPath, legacy);
        *///?}
    }

    /** A {@code minecraft:model} definition entry. */
    public static JsonObject reference(String modelId) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelId);
        return model;
    }
}
