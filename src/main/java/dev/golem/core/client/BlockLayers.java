package dev.golem.core.client;

/**
 * A block model's {@code render_type} as the block's chunk layer, on every node.
 *
 * <p>26.1, NeoForge and Forge read {@code render_type} from the model JSON themselves. Fabric below 26.1 has no
 * model-level layer: every block draws on the solid layer unless something registers it with Fabric API's
 * {@code BlockRenderLayerMap}, so a cutout torch or grate showed its transparent texels as a coloured plate
 * (2026-10-06). There, {@link #install} reads each of the namespace's blockstates after every client resource
 * reload, follows their models up the parent chain to the first {@code render_type}, and registers the most
 * transparent layer the block's models ask for (Fabric's layer is per block, not per model). Elsewhere it does
 * nothing. Logs {@code Render layers from render_type: N <namespace> block(s)}.
 */
public final class BlockLayers {

    private BlockLayers() {}

    /** Installs the legacy-Fabric bridge for {@code namespace}'s blocks; a no-op on every other node. */
    public static void install(String namespace) {
        //? if fabric && <26.1 {
        /*ClientResourceReloads.register(namespace, "block_layers", () -> apply(namespace,
                net.minecraft.client.Minecraft.getInstance().getResourceManager()));
        *///?}
    }

    //? if fabric && <26.1 {
    /*private static final java.util.regex.Pattern MODEL = java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"");
    private static final java.util.List<String> ORDER = java.util.List.of(
            "minecraft:solid", "minecraft:cutout_mipped", "minecraft:cutout", "minecraft:translucent");

    private static void apply(String namespace, net.minecraft.server.packs.resources.ResourceManager resources) {
        java.util.Map<String, String> modelLayers = new java.util.HashMap<>();
        int count = 0;
        for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
            var id = dev.golem.core.Ids.keyId(entry.getKey());
            if (!id.getNamespace().equals(namespace)) continue;
            String states = read(resources, dev.golem.core.Ids.of(namespace, "blockstates/" + id.getPath() + ".json"));
            if (states == null) continue;
            String layer = null;
            java.util.regex.Matcher m = MODEL.matcher(states);
            while (m.find()) {
                String found = modelLayers.computeIfAbsent(m.group(1), model -> renderType(resources, model));
                if (found != null && (layer == null || ORDER.indexOf(found) > ORDER.indexOf(layer))) layer = found;
            }
            net.minecraft.client.renderer.RenderType type = switch (layer == null ? "" : layer) {
                case "minecraft:cutout" -> net.minecraft.client.renderer.RenderType.cutout();
                case "minecraft:cutout_mipped" -> net.minecraft.client.renderer.RenderType.cutoutMipped();
                case "minecraft:translucent" -> net.minecraft.client.renderer.RenderType.translucent();
                default -> null;
            };
            if (type == null) continue;
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(entry.getValue(), type);
            count++;
        }
        dev.golem.core.GolemCore.LOG.info("Render layers from render_type: {} {} block(s)", count, namespace);
    }

    private static String renderType(net.minecraft.server.packs.resources.ResourceManager resources, String model) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        String current = model.contains(":") ? model : "minecraft:" + model;
        while (current != null && seen.add(current)) {
            var id = dev.golem.core.Ids.parse(current);
            String text = read(resources, dev.golem.core.Ids.of(id.getNamespace(), "models/" + id.getPath() + ".json"));
            if (text == null) return null;
            com.google.gson.JsonObject json;
            try {
                json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            } catch (RuntimeException e) {
                return null;
            }
            if (json.has("render_type")) {
                String type = json.get("render_type").getAsString();
                return type.contains(":") ? type : "minecraft:" + type;
            }
            String parent = json.has("parent") ? json.get("parent").getAsString() : null;
            current = parent == null ? null : parent.contains(":") ? parent : "minecraft:" + parent;
        }
        return null;
    }

    private static String read(net.minecraft.server.packs.resources.ResourceManager resources,
            net.minecraft.resources.ResourceLocation id) {
        var resource = resources.getResource(id);
        if (resource.isEmpty()) return null;
        try (java.io.BufferedReader reader = resource.get().openAsReader()) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
    *///?}
}
