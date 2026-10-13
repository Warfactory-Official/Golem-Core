package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p>What comes out is ordinary vanilla model and blockstate JSON: it goes through the same parser and bake
 * pipeline, and connected-texture engines see it as they see a file-loaded model. The one mode a generated model
 * cannot support is the model-keyed one, where a model JSON declares a custom loader; there is no file to carry
 * the key.
 *
 * <h2>File wins</h2>
 *
 * <p>The delivery layer consults this registry only where no resource exists at the path. A real file always
 * overrides a declaration, so resource packs and hand-authored exceptions keep working.
 *
 * <h2>What is checked, and when</h2>
 *
 * <p>Everything answerable from the declaration alone is checked at {@link Declaration#register()}: malformed
 * ids, duplicate slots, a rotation that is not a quarter turn, a dispatch pointing at a model its shape never
 * emits. Whether a named texture exists needs a resource manager, so {@link #sprites()} hands the delivery layer
 * the full set to check at reload.
 */
public final class Dejson {

    private static final Map<String, JsonObject> MODELS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> STATES = new ConcurrentHashMap<>();
    private static final Map<String, Declaration> DECLARATIONS = new ConcurrentHashMap<>();
    private static final Map<String, ItemDeclaration> ITEM_DECLARATIONS = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> ITEM_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> GENERATED_SPRITES = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> STANDALONE_MODELS = new ConcurrentHashMap<>();

    private Dejson() {}

    /** Begins a declaration for {@code <namespace>:<path>}, matching the block's registry id. */
    public static Declaration block(String namespace, String path) {
        return new Declaration(namespace, path);
    }

    /**
     * Begins a declaration for a plain item, matching the item's registry id.
     *
     * <p>An item has no blockstate, so a declaration is one shape; most are a sprite or three stacked on
     * {@code item/generated}.
     *
     * <p>A block's own item is attached with {@link Declaration#blockItem()}. This is for items that are only
     * items, and for block items whose block loads its model from a file.
     */
    public static ItemDeclaration item(String namespace, String path) {
        return new ItemDeclaration(namespace, path);
    }

    /**
     * Declares a model no one block or item owns: a part a multipart blockstate assembles, a model a
     * renderer loads by id, a parent other models inherit from.
     *
     * <p>A block's or item's own models belong in its {@link #block} or {@link #item} declaration, where the
     * blockstate or definition pointing at them is checked against them.
     *
     * @param modelId the full model id, e.g. {@code hbm:block/pipe_anchor_down}
     */
    public static void model(String modelId, Shape shape) {
        Models.requireId(modelId, "model");
        if (shape == null) {
            throw new DejsonException(modelId + " was given a null shape");
        }
        Map<String, JsonObject> built = shape.models(modelId);
        if (built.size() != 1 || !built.containsKey(modelId)) {
            throw new DejsonException(modelId + " has a shape that emits more than its own model: "
                    + built.keySet());
        }
        JsonObject model = built.get(modelId);
        if (MODELS.putIfAbsent(modelId, model) != null) {
            throw new DejsonException("model " + modelId + " is declared twice");
        }
        STANDALONE_MODELS.put(modelId, model);
    }

    /** Every model declared through {@link #model}, keyed by model id. */
    public static Map<String, JsonObject> standaloneModels() {
        return Collections.unmodifiableMap(STANDALONE_MODELS);
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

    /**
     * Declares that {@code source} is recoloured into one sprite per permutation while the atlas is
     * stitched, so those sprites are never files.
     *
     * <p>The texture existence check would otherwise report each permutation missing. It still requires the
     * greyscale source art to be on disk and reports a permutation of a missing source against the source.
     *
     * @param source       the sprite id the art is shipped under, e.g. {@code hbm:items/plate_cast}
     * @param separator    what joins the source id and the permutation name
     * @param permutations the permutation names, e.g. the material names
     * @see dev.golem.core.render.Sprites
     */
    public static void generatedSprites(String source, String separator, Collection<String> permutations) {
        for (String permutation : permutations) {
            GENERATED_SPRITES.put(source + separator + permutation, source);
        }
    }

    /** Generated sprite id to the source it is computed from. */
    public static Map<String, String> generatedSprites() {
        return Collections.unmodifiableMap(GENERATED_SPRITES);
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
        sprites.addAll(spritesOf(STANDALONE_MODELS));
        return sprites;
    }

    /** Drops every declaration. Tests only; nothing in a running game unregisters a block. */
    public static void reset() {
        MODELS.clear();
        STATES.clear();
        DECLARATIONS.clear();
        ITEM_DECLARATIONS.clear();
        ITEM_DEFINITIONS.clear();
        GENERATED_SPRITES.clear();
        STANDALONE_MODELS.clear();
        States.reset();
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
        private boolean authoredModel;
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
         * <p>An OBJ machine has no block model of its own: vanilla bakes an empty placeholder carrying a particle
         * texture, and every machine points at the same one. The blockstate must still exist and name every state.
         *
         * <p>The blockstate is built on {@code sharedModel} rather than this block's own id, so a dispatch that
         * rotates or selects by suffix runs against the shared model.
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
         * <p>See {@link #blockItemDirect()} for the other spelling; the two bake identically and differ only in
         * the model id the definition names.
         */
        public Declaration blockItem() {
            definitionModel = itemId;
            return item(Models.blockItem(baseModel));
        }

        /**
         * The same block item, with the 26.1 definition naming the <i>block</i> model directly
         * instead of going through the item model.
         *
         * <p>A definition naming {@code hbm:block/foo} and one naming an item model whose only content is
         * {@code parent: hbm:block/foo} bake to the same thing. The item model is emitted either way, because
         * below 26.1 there is no definition layer and {@code models/item/<path>.json} is all the renderer reads.
         */
        public Declaration blockItemDirect() {
            definitionModel = baseModel;
            return item(Models.blockItem(baseModel));
        }

        /**
         * A block whose blockstate is regular but whose model is not: the states are a facing or a single
         * variant, and the geometry is hand-built {@code elements} that {@link Models} does not express. The
         * blockstate names the block's own model id and the declaration emits no model for it.
         *
         * <p>{@link #rendered} is the counterpart where the model belongs to another block.
         */
        public Declaration statesOnly() {
            return statesOnly(baseModel);
        }

        /** The same, where the blockstate names an authored model other than the block's own. */
        public Declaration statesOnly(String modelId) {
            Models.requireId(modelId, "authored model");
            this.authoredModel = true;
            this.renderBase = modelId;
            return models(Models.none());
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
            return renderBase != null && !authoredModel;
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
                throw new DejsonException(blockId + " is declared "
                        + (authoredModel ? "statesOnly(" : "rendered(") + renderBase
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
     * <p>Up to 1.21.x an item is a model at {@code models/item/<path>.json}. From 26.1 it is also a definition
     * at {@code items/<path>.json} that names a model. Both are emitted from the one declaration.
     */
    public static final class ItemDeclaration {

        private final String itemId;
        private final String definitionId;

        private Shape shape;
        private String definitionModel;
        private String selectProperty;
        private ItemModel definition;
        private boolean oversizedInGui;
        private List<Tint> tints = List.of();
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

        /**
         * An item that shows a model somebody else owns, and emits none itself (the block analogue is
         * {@link Declaration#rendered}).
         *
         * <p>Below 26.1 {@code legacy-assets} derives {@code models/item/<path>.json} as a bare parent pointing
         * at whatever the definition names.
         */
        public ItemDeclaration shares(String modelId) {
            Models.requireId(modelId, "shared model");
            this.definitionModel = modelId;
            return models(Models.none());
        }

        /**
         * An item that picks its model from a property carrying a variant number, the way an enum-backed
         * family does: the models this declaration emits <i>are</i> the cases, in the order the shape
         * emits them, and variant <i>n</i> takes the <i>n</i>th.
         *
         * <p>The order is the enum's, so a {@link Models#group} listed in constant order is the whole declaration.
         * Case zero is the fallback for a stack without the property, and must be the family's own model id,
         * since that is the model other targets load by path.
         *
         * <pre>{@code Dejson.item("hbm", "ammo_arty")
         *         .models(Models.group().and("", Models.generated("hbm:items/ammo_arty"))
         *                               .and("_classic", Models.generated("hbm:items/ammo_arty_classic")))
         *         .selects("hbm:item_variant")}</pre>
         */
        public ItemDeclaration selects(String property) {
            Models.requireId(property, "select property");
            this.selectProperty = property;
            return this;
        }

        /**
         * The tints the definition layers over its model, positionally: entry <i>n</i> colours
         * {@code layer<n>}. A two-layer item whose overlay alone takes the stack's colour passes
         * {@link Tints#constant} at {@code -1} first.
         */
        public ItemDeclaration tints(Tint... tints) {
            if (tints.length == 0) {
                throw new DejsonException(definitionId + " was given an empty tint list");
            }
            this.tints = List.of(tints);
            return this;
        }

        /**
         * The 26.1 definition's model tree, stated in full: a {@code special} renderer, a
         * {@code select} whose cases are not all this item's own models, a type the mod defines.
         *
         * <p>A given shape still emits the item's own model, which the tree may or may not name; with no shape
         * only the definition is emitted.
         */
        public ItemDeclaration definition(ItemModel definition) {
            if (definition == null) {
                throw new DejsonException(definitionId + " was given a null definition");
            }
            this.definition = definition;
            return this;
        }

        /**
         * Sets the definition's {@code oversized_in_gui} flag: the icon is allowed to draw past its slot,
         * as a large machine's does, instead of being clipped to it.
         */
        public ItemDeclaration oversizedInGui() {
            this.oversizedInGui = true;
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
            if (shape == null && definition == null) {
                throw new DejsonException(definitionId + " declares no model; call models(...)");
            }

            Map<String, JsonObject> built = shape == null ? Map.of() : shape.models(itemId);
            if (definition != null) {
                if (selectProperty != null || definitionModel != null || !tints.isEmpty()) {
                    throw new DejsonException(definitionId + " states its definition in full; "
                            + "shares(), selects() and tints() belong inside it");
                }
                if (built.size() > 1 || (built.size() == 1 && !built.containsKey(itemId))) {
                    throw new DejsonException(definitionId
                            + " has a shape that emits more than its own model: " + built.keySet());
                }
            } else if (selectProperty != null) {
                if (definitionModel != null) {
                    throw new DejsonException(definitionId + " both shares(" + definitionModel
                            + ") and selects(" + selectProperty + ")");
                }
                if (built.size() < 2) {
                    throw new DejsonException(definitionId + " selects(" + selectProperty
                            + ") but its shape emits " + built.size() + " model(s): " + built.keySet());
                }
                if (!itemId.equals(built.keySet().iterator().next())) {
                    throw new DejsonException(definitionId + " selects(" + selectProperty
                            + ") but its first case is not " + itemId + ": " + built.keySet());
                }
            } else if (definitionModel == null) {
                if (built.size() != 1 || !built.containsKey(itemId)) {
                    throw new DejsonException(definitionId
                            + " has a shape that emits more than its own model: " + built.keySet());
                }
            } else if (!built.isEmpty()) {
                throw new DejsonException(definitionId + " shares(" + definitionModel
                        + ") but its shape still emits " + built.keySet());
            }

            if (MODELS.containsKey(itemId) && (definition == null || built.containsKey(itemId))) {
                throw new DejsonException("model " + itemId + " is declared twice; "
                        + definitionId + " collides with an earlier declaration");
            }
            if (ITEM_DECLARATIONS.putIfAbsent(definitionId, this) != null) {
                throw new DejsonException(definitionId + " is declared twice");
            }

            this.models = Collections.unmodifiableMap(new LinkedHashMap<>(built));
            MODELS.putAll(built);
            JsonObject file;
            if (definition != null) {
                file = new JsonObject();
                file.add("model", definition.toJson());
            } else {
                file = selectProperty == null
                        ? Models.itemDefinition(definitionModel == null ? itemId : definitionModel, tints)
                        : Models.itemSelection(selectProperty, List.copyOf(built.keySet()), tints);
            }
            if (oversizedInGui) {
                file.addProperty("oversized_in_gui", true);
            }
            ITEM_DEFINITIONS.put(itemId, file);
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
