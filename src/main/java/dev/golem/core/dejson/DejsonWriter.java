package dev.golem.core.dejson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the declared models, blockstates, item definitions, loot tables and tags out as the JSON
 * files they stand in for.
 *
 * <p>This is the delivery layer in its simplest possible form: the build emits the files, and the
 * game loads them the way it loads every other file. Nothing is intercepted, nothing is resolved at
 * runtime, and a connected-texture engine, a resource pack, JEI and the model debugger all see
 * exactly what they see today, because what they see today is what this writes.
 *
 * <p>The interesting half is {@link #verify}, which is what makes deleting a file safe. A
 * declaration is only worth anything if it says the same thing as the file it replaces, and that is
 * a question with a yes-or-no answer as long as the file is still there. So the order is: declare,
 * verify against the file, delete the file. After the deletion there is nothing left to compare
 * against, which is why the comparison has to happen first and has to be part of the build.
 */
public final class DejsonWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DejsonWriter() {}

    /** What one write did: how many files it emitted and how many an authored file already covered. */
    public record Report(int written, int deferred) {}

    /** One disagreement between a declaration and the file it stands in for. */
    public record Mismatch(String path, String detail) {}

    /**
     * Emits every declaration that no authored file already covers.
     *
     * <p>{@code authored} is the hand-written resource root. A path that exists there is left to it
     * — that is the "file wins" rule, applied once at build time instead of on every lookup. The
     * two roots are then stacked by the build, authored last.
     */
    public static Report write(Path out, Path authored) throws IOException {
        int written = 0;
        int deferred = 0;
        for (Map.Entry<String, JsonObject> entry : files().entrySet()) {
            Path relative = Path.of(entry.getKey());
            if (authored != null && Files.exists(authored.resolve(relative))) {
                deferred++;
                continue;
            }
            Path target = out.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.writeString(target, GSON.toJson(entry.getValue()) + "\n", StandardCharsets.UTF_8);
            written++;
        }
        return new Report(written, deferred);
    }

    /**
     * Compares every declaration against the authored file it stands in for, where one exists.
     *
     * <p>Comparison is on the parsed JSON, not on the text: key order and whitespace are not
     * meaningful to any loader, and the files on disk do not agree on either. A bare {@code parent}
     * such as {@code block/cube_all} is read as the {@code minecraft:} one it resolves to, which is
     * also not a difference — both spellings are on disk and they load the same.
     */
    public static List<Mismatch> verify(Path authored) {
        List<Mismatch> mismatches = new ArrayList<>();
        files().forEach((path, generated) -> {
            Path file = authored.resolve(path);
            if (!Files.exists(file)) {
                return;
            }
            JsonObject shipped;
            try {
                shipped = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException failure) {
                throw new UncheckedIOException("could not read " + file, failure);
            }
            if (!namespaced(shipped).equals(namespaced(generated))) {
                mismatches.add(new Mismatch(path, "declared " + GSON.toJson(generated)
                        + " but the file says " + GSON.toJson(shipped)));
            }
        });
        return mismatches;
    }

    /**
     * Sprites a declaration names that no texture file backs.
     *
     * <p>This is the failure the generated model cannot catch on its own. A model that names a
     * texture which is not there still parses, still bakes, and comes out as the missing-texture
     * chequerboard on a block in a world — and because the model was generated, there is no file
     * for anyone to notice the typo in. The set of names is known at declaration time and the set
     * of files is on disk, so the two can simply be compared, and this is the only check in the
     * layer that needs the texture tree rather than the model tree.
     *
     * <p>{@code hbm:blocks/steel} is a sprite id, and the file it wants is
     * {@code assets/hbm/textures/blocks/steel.png}. Only namespaces whose texture tree is in the
     * authored root are checked. Vanilla's sprites live in the game jar and another mod's live in
     * that mod's, so their absence here says nothing — and a consumer that ships one file under
     * {@code assets/minecraft} must not thereby make every vanilla sprite look missing.
     */
    public static List<String> missingSprites(Path authored) {
        List<String> missing = new ArrayList<>();
        Map<String, String> generated = Dejson.generatedSprites();
        for (String declared : Dejson.sprites()) {
            // A generated sprite has no file of its own; what has to exist is the art it is computed
            // from, and reporting that is what points at the fixable thing.
            String sprite = generated.getOrDefault(declared, declared);
            int colon = sprite.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : sprite.substring(0, colon);
            String path = sprite.substring(colon + 1);
            Path textures = authored.resolve("assets").resolve(namespace).resolve("textures");
            if ("minecraft".equals(namespace) || !Files.isDirectory(textures)) {
                continue;
            }
            if (!Files.exists(textures.resolve(path + ".png")) && !missing.contains(sprite)) {
                missing.add(sprite);
            }
        }
        return missing;
    }

    /** How many authored files the declarations reproduce exactly, and would let go. */
    public static int covered(Path authored) {
        int covered = 0;
        for (String path : files().keySet()) {
            if (Files.exists(authored.resolve(path))) {
                covered++;
            }
        }
        return covered;
    }

    /**
     * Every file the current declarations stand for, keyed by its path under the resource root.
     *
     * <p>Assets are derived from the block and item declarations; data files come from {@link Data}
     * already keyed by path, because a loot table and a tag have no shape in common to derive one
     * from.
     */
    public static Map<String, JsonObject> files() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (Dejson.Declaration declaration : Dejson.declarations()) {
            files.put(path(declaration.blockId(), "blockstates"),
                    Dejson.blockState(declaration.blockId()));
            declaration.models().forEach((id, model) -> files.put(path(id, "models"), model));
            if (declaration.hasItem()) {
                files.put(path(declaration.blockId(), "items"),
                        Dejson.itemDefinition(declaration.itemId()));
            }
        }
        for (Dejson.ItemDeclaration declaration : Dejson.itemDeclarations()) {
            declaration.models().forEach((id, model) -> files.put(path(id, "models"), model));
            files.put(path(declaration.definitionId(), "items"),
                    Dejson.itemDefinition(declaration.itemId()));
        }
        files.putAll(Data.files());
        return files;
    }

    /** {@code hbm:block/foo} in {@code models} becomes {@code assets/hbm/models/block/foo.json}. */
    private static String path(String id, String folder) {
        int colon = id.indexOf(':');
        return "assets/" + id.substring(0, colon) + "/" + folder + "/" + id.substring(colon + 1) + ".json";
    }

    private static JsonObject namespaced(JsonObject model) {
        if (!model.has("parent") || !model.get("parent").isJsonPrimitive()) {
            return model;
        }
        String parent = model.get("parent").getAsString();
        if (parent.indexOf(':') >= 0) {
            return model;
        }
        JsonObject copy = model.deepCopy();
        copy.addProperty("parent", "minecraft:" + parent);
        return copy;
    }
}
