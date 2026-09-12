package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block models and blockstates declared in code instead of written as JSON files.
 *
 * <p>A declaration is made beside the block's registration and says what the hand-written files
 * would have said:
 *
 * <pre>{@code
 * Dejson.block("hbm", "crate_steel")
 *       .models(Models.cubeAll("hbm:blocks/crate_steel"))
 *       .states(Variants.facing())
 *       .blockItem()
 *       .register();
 * }</pre>
 *
 * <p>What comes out is ordinary vanilla model and blockstate JSON, which is the whole point. It
 * goes through the same parser, bakes on the same pipeline, and is seen by connected-texture
 * engines exactly as a file-loaded model is — the texture-keyed ones because they sit beside the
 * sprite rather than the model, the post-bake wrapping ones because they wrap whatever was baked.
 * The one connected-texture mode a generated model cannot support is the model-keyed one, where a
 * model JSON declares a custom loader: there is no file to carry the key. That mode is not used
 * here and should not be adopted.
 *
 * <h2>File wins</h2>
 *
 * <p>The delivery layer consults this registry only where no resource exists at the path. A real
 * file always overrides a declaration, which keeps resource packs working, keeps a hand-authored
 * exception possible, and makes adoption incremental: deleting the JSON file <i>is</i> the
 * migration step, and putting it back is the way out.
 *
 * <h2>What is checked, and when</h2>
 *
 * <p>Everything answerable from the declaration alone is answered at {@link Declaration#register()}
 * — malformed ids, duplicate slots, a rotation that is not a quarter turn, a dispatch pointing at a
 * model its shape never emits. Whether a named texture actually exists cannot be answered until
 * there is a resource manager, so {@link #sprites()} hands the delivery layer the full set to check
 * at reload.
 */
public final class Dejson {

    private static final Map<String, JsonObject> MODELS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> STATES = new ConcurrentHashMap<>();
    private static final Map<String, Declaration> DECLARATIONS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> ITEM_DEFINITIONS = new ConcurrentHashMap<>();

    private Dejson() {}

    /** Begins a declaration for {@code <namespace>:<path>}, matching the block's registry id. */
    public static Declaration block(String namespace, String path) {
        return new Declaration(namespace, path);
    }

    /** The generated {@code models/<path>.json} for a model id, or null if none was declared. */
    public static JsonObject model(String modelId) {
        return MODELS.get(modelId);
    }

    /** The generated {@code blockstates/<path>.json} for a block id, or null if none was declared. */
    public static JsonObject blockState(String blockId) {
        return STATES.get(blockId);
    }

    /**
     * The generated {@code items/<path>.json} for an item id, or null if none was declared. Only the
     * targets that have the item-definition layer read this; the others load
     * {@link #model(String)} at {@code models/item/<path>.json} directly.
     */
    public static JsonObject itemDefinition(String itemId) {
        return ITEM_DEFINITIONS.get(itemId);
    }

    public static Declaration declaration(String blockId) {
        return DECLARATIONS.get(blockId);
    }

    public static Collection<Declaration> declarations() {
        return Collections.unmodifiableCollection(DECLARATIONS.values());
    }

    public static Set<String> modelIds() {
        return Collections.unmodifiableSet(MODELS.keySet());
    }

    /** Every sprite id named by every declaration, for the delivery layer's existence check. */
    public static Set<String> sprites() {
        Set<String> sprites = new LinkedHashSet<>();
        for (Declaration declaration : DECLARATIONS.values()) {
            sprites.addAll(declaration.sprites());
        }
        return sprites;
    }

    /** Drops every declaration. Tests only; nothing in a running game unregisters a block. */
    public static void reset() {
        MODELS.clear();
        STATES.clear();
        DECLARATIONS.clear();
        ITEM_DEFINITIONS.clear();
    }

    /** One block's declaration, built fluently and checked when it registers. */
    public static final class Declaration {

        private final String blockId;
        private final String baseModel;
        private final String itemId;

        private Shape shape;
        private Dispatch dispatch;
        private Shape itemShape;
        private Map<String, JsonObject> models = Collections.emptyMap();

        private Declaration(String namespace, String path) {
            if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
                throw new DejsonException("block declaration needs a namespace and a path");
            }
            this.blockId = namespace + ":" + path;
            this.baseModel = namespace + ":block/" + path;
            this.itemId = namespace + ":item/" + path;
            Models.requireId(blockId, "block");
        }

        public Declaration models(Shape shape) {
            if (shape == null) {
                throw new DejsonException(blockId + " was given a null shape");
            }
            this.shape = shape;
            return this;
        }

        public Declaration states(Dispatch dispatch) {
            if (dispatch == null) {
                throw new DejsonException(blockId + " was given a null dispatch");
            }
            this.dispatch = dispatch;
            return this;
        }

        public Declaration item(Shape itemShape) {
            this.itemShape = itemShape;
            return this;
        }

        /** The usual item model: a bare parent pointing at this block's own base model. */
        public Declaration blockItem() {
            return item(Models.blockItem(baseModel));
        }

        /** The block's registry id, and the path its blockstate JSON would live at. */
        public String blockId() {
            return blockId;
        }

        /** The block's own model id, {@code <namespace>:block/<path>}. */
        public String baseModel() {
            return baseModel;
        }

        /** The block item's model id, {@code <namespace>:item/<path>}. */
        public String itemId() {
            return itemId;
        }

        /** Whether this declaration carries an item model, and so an item definition too. */
        public boolean hasItem() {
            return itemShape != null;
        }

        public Dispatch dispatch() {
            return dispatch;
        }

        /** The blockstate properties this declaration names, for the coverage check. */
        public Set<String> properties() {
            return dispatch == null ? Collections.emptySet() : dispatch.properties();
        }

        /** Every variant key this declaration emits. */
        public Set<String> variantKeys() {
            return dispatch == null ? Collections.emptySet() : dispatch.placements().keySet();
        }

        public Map<String, JsonObject> models() {
            return models;
        }

        public JsonObject blockStateJson() {
            return dispatch.toJson(baseModel);
        }

        public Set<String> sprites() {
            Set<String> sprites = new LinkedHashSet<>();
            for (JsonObject model : models.values()) {
                JsonObject textures = model.getAsJsonObject("textures");
                if (textures == null) {
                    continue;
                }
                for (String slot : textures.keySet()) {
                    String value = textures.get(slot).getAsString();
                    if (!value.startsWith("#")) {
                        sprites.add(value);
                    }
                }
            }
            return sprites;
        }

        /** Checks the declaration and publishes it. Throws rather than registering a broken one. */
        public Declaration register() {
            if (shape == null) {
                throw new DejsonException(blockId + " declares no models; call models(...)");
            }
            if (dispatch == null) {
                throw new DejsonException(blockId + " declares no blockstate; call states(...)");
            }

            Map<String, JsonObject> built = new LinkedHashMap<>(shape.models(baseModel));
            if (itemShape != null) {
                Map<String, JsonObject> item = itemShape.models(itemId);
                if (item.size() != 1 || !item.containsKey(itemId)) {
                    throw new DejsonException(blockId
                            + " has an item shape that emits more than its own model: " + item.keySet());
                }
                built.putAll(item);
            }

            Set<String> referenced = dispatch.referencedModels(baseModel);
            Set<String> missing = new LinkedHashSet<>(referenced);
            missing.removeAll(built.keySet());
            if (!missing.isEmpty()) {
                throw new DejsonException(blockId + " points at " + missing
                        + " but its shape only emits " + built.keySet()
                        + "; the shape and the blockstate disagree");
            }

            for (String id : built.keySet()) {
                if (MODELS.containsKey(id)) {
                    throw new DejsonException("model " + id + " is declared twice; "
                            + blockId + " collides with an earlier declaration");
                }
            }
            if (DECLARATIONS.putIfAbsent(blockId, this) != null) {
                throw new DejsonException(blockId + " is declared twice");
            }

            this.models = Collections.unmodifiableMap(built);
            MODELS.putAll(built);
            STATES.put(blockId, blockStateJson());
            if (itemShape != null) {
                ITEM_DEFINITIONS.put(itemId, Models.itemDefinition(itemId));
            }
            return this;
        }
    }
}
