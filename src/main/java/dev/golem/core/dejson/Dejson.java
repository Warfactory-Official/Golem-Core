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
    private static final Map<String, ItemDeclaration> ITEM_DECLARATIONS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> ITEM_DEFINITIONS = new ConcurrentHashMap<>();

    private Dejson() {}

    /** Begins a declaration for {@code <namespace>:<path>}, matching the block's registry id. */
    public static Declaration block(String namespace, String path) {
        return new Declaration(namespace, path);
    }

    /**
     * Begins a declaration for a plain item, matching the item's registry id.
     *
     * <p>An item is the smaller half of the same idea and by far the larger half of the corpus: it
     * has no blockstate, so a declaration is one shape and nothing else. Almost all of them are a
     * sprite or three stacked on {@code item/generated}.
     *
     * <p>A block's own item is not declared this way — {@link Declaration#blockItem()} attaches it
     * to the block, so the two cannot drift apart. This is for items that are only items, and for
     * the block items whose block still loads its model from a file.
     */
    public static ItemDeclaration item(String namespace, String path) {
        return new ItemDeclaration(namespace, path);
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

    public static Collection<ItemDeclaration> itemDeclarations() {
        return Collections.unmodifiableCollection(ITEM_DECLARATIONS.values());
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
        for (ItemDeclaration declaration : ITEM_DECLARATIONS.values()) {
            sprites.addAll(declaration.sprites());
        }
        return sprites;
    }

    /** Drops every declaration. Tests only; nothing in a running game unregisters a block. */
    public static void reset() {
        MODELS.clear();
        STATES.clear();
        DECLARATIONS.clear();
        ITEM_DECLARATIONS.clear();
        ITEM_DEFINITIONS.clear();
        Data.reset();
    }

    /** One block's declaration, built fluently and checked when it registers. */
    public static final class Declaration {

        private final String blockId;
        private final String baseModel;
        private final String itemId;

        private Shape shape;
        private Dispatch dispatch;
        private String renderBase;
        private Shape itemShape;
        private String definitionModel;
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

        /**
         * Declares a block drawn by a renderer: the blockstate points at a shared model this block
         * does not own, and the block emits no models of its own.
         *
         * <p>An OBJ machine has no block model to speak of — what vanilla bakes for it is an empty
         * placeholder carrying a particle texture, and every one of them points at the same one.
         * The blockstate still has to exist, and still has to name every state, which is why 150
         * near-identical files are on disk. This says that in one line, and says the true thing
         * while it is at it: this block's appearance is not in a model file.
         *
         * <p>The blockstate is built on {@code sharedModel} rather than on this block's own id, so
         * a dispatch that rotates or selects by suffix still works — it just does so against
         * somebody else's model, which is the caller's business to get right.
         */
        public Declaration rendered(String sharedModel) {
            Models.requireId(sharedModel, "shared model");
            this.renderBase = sharedModel;
            return models(Models.none());
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

        /**
         * The block's item: a model that is a bare parent pointing at the block's own model, and a
         * 26.1 item definition naming that item model.
         *
         * <p>See {@link #blockItemDirect()} for the other spelling. The two bake identically; they
         * differ only in which model id the definition names, and both are on disk here.
         */
        public Declaration blockItem() {
            definitionModel = itemId;
            return item(Models.blockItem(baseModel));
        }

        /**
         * The same block item, with the 26.1 definition naming the <i>block</i> model directly
         * instead of going through the item model.
         *
         * <p>This is not a different-looking item. A definition naming {@code hbm:block/foo} and
         * one naming an item model whose only content is {@code parent: hbm:block/foo} bake to the
         * same thing, and 245 of the definitions here take the short way while 141 take the long
         * one. The item model is emitted either way, because below 26.1 there is no definition
         * layer and {@code models/item/<path>.json} is the only thing the renderer looks for. The
         * short-way block items ship no such file; a consumer that has been deriving one from the
         * definition at build time can stop, because the declaration emits it from the same place
         * the block model comes from.
         */
        public Declaration blockItemDirect() {
            definitionModel = baseModel;
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

        /** The model the blockstate is built on: this block's own, or the shared one it is drawn by. */
        public String stateBase() {
            return renderBase == null ? baseModel : renderBase;
        }

        /** Whether this block's appearance comes from a renderer rather than from a model it owns. */
        public boolean isRendered() {
            return renderBase != null;
        }

        public JsonObject blockStateJson() {
            return dispatch.toJson(stateBase());
        }

        public Set<String> sprites() {
            return spritesOf(models);
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

            if (renderBase == null) {
                Set<String> referenced = dispatch.referencedModels(baseModel);
                Set<String> missing = new LinkedHashSet<>(referenced);
                missing.removeAll(built.keySet());
                if (!missing.isEmpty()) {
                    throw new DejsonException(blockId + " points at " + missing
                            + " but its shape only emits " + built.keySet()
                            + "; the shape and the blockstate disagree");
                }
            } else if (!built.isEmpty()) {
                throw new DejsonException(blockId + " is declared rendered(" + renderBase
                        + ") but its shape still emits " + built.keySet());
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
                ITEM_DEFINITIONS.put(itemId,
                        Models.itemDefinition(definitionModel == null ? itemId : definitionModel));
            }
            return this;
        }
    }

    /**
     * One item's declaration: a shape, and the two files it stands in for.
     *
     * <p>There are two files because the eras disagree about what an item model <i>is</i>. Up to
     * 1.21.x an item is a model at {@code models/item/<path>.json} and nothing else. From 26.1 it
     * is a <i>definition</i> at {@code items/<path>.json} that names a model, and the model stays
     * where it was. Both are emitted from the one declaration, so neither target has to be thought
     * about at the call site.
     */
    public static final class ItemDeclaration {

        private final String itemId;
        private final String definitionId;

        private Shape shape;
        private Map<String, JsonObject> models = Collections.emptyMap();

        private ItemDeclaration(String namespace, String path) {
            if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
                throw new DejsonException("item declaration needs a namespace and a path");
            }
            this.definitionId = namespace + ":" + path;
            this.itemId = namespace + ":item/" + path;
            Models.requireId(definitionId, "item");
        }

        public ItemDeclaration models(Shape shape) {
            if (shape == null) {
                throw new DejsonException(definitionId + " was given a null shape");
            }
            this.shape = shape;
            return this;
        }

        /** The item's registry id, and the path its item definition would live at. */
        public String definitionId() {
            return definitionId;
        }

        /** The item's model id, {@code <namespace>:item/<path>}. */
        public String itemId() {
            return itemId;
        }

        public Map<String, JsonObject> models() {
            return models;
        }

        public Set<String> sprites() {
            return spritesOf(models);
        }

        public ItemDeclaration register() {
            if (shape == null) {
                throw new DejsonException(definitionId + " declares no model; call models(...)");
            }

            Map<String, JsonObject> built = shape.models(itemId);
            if (built.size() != 1 || !built.containsKey(itemId)) {
                throw new DejsonException(definitionId
                        + " has a shape that emits more than its own model: " + built.keySet());
            }

            if (MODELS.containsKey(itemId)) {
                throw new DejsonException("model " + itemId + " is declared twice; "
                        + definitionId + " collides with an earlier declaration");
            }
            if (ITEM_DECLARATIONS.putIfAbsent(definitionId, this) != null) {
                throw new DejsonException(definitionId + " is declared twice");
            }

            this.models = Collections.unmodifiableMap(new LinkedHashMap<>(built));
            MODELS.putAll(built);
            ITEM_DEFINITIONS.put(itemId, Models.itemDefinition(itemId));
            return this;
        }
    }

    private static Set<String> spritesOf(Map<String, JsonObject> models) {
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
}
