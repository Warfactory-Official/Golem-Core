package com.wf.gemrender.spike;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfImporter;
import com.wf.gemrender.texture.ModelTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code -PimportList=<file>}: imports every id in the file (one per line) through {@link GltfImporter},
 * one at a time, releasing each before the next; writes {@code <file>.result.json}, then quits.
 */
public final class ImportSpike {
	private static final String LIST = System.getProperty("gemrender.importlist", "");

	private static boolean started;

	private ImportSpike() {
	}

	public static void tick() {
		if (LIST.isEmpty() || started || Minecraft.getInstance().getOverlay() != null) {
			return;
		}
		started = true;
		Thread worker = new Thread(ImportSpike::run, "GemRender import spike");
		worker.setDaemon(true);
		worker.start();
	}

	private static void run() {
		Path list = Path.of(LIST);
		JsonArray results = new JsonArray();
		int ok = 0;
		int failed = 0;
		try {
			List<String> ids = Files.readAllLines(list, StandardCharsets.UTF_8);
			for (String line : ids) {
				if (line.isBlank()) {
					continue;
				}
				JsonObject entry = new JsonObject();
				entry.addProperty("id", line.trim());
				try {
					GemRenderGltfModel model = GltfImporter.load(Ids.parse(line.trim()));
					entry.addProperty("atlas", model.atlas() != null);
					entry.addProperty("textures", model.textures().size());
					entry.addProperty("joints", model.jointCount());
					JsonArray clips = new JsonArray();
					model.animations().keySet().forEach(clips::add);
					entry.add("animations", clips);
					// Queued after the import's own texture registrations, so it releases what they made.
					CompletableFuture<Void> released = new CompletableFuture<>();
					Runnable release = () -> {
						for (ResourceLocation texture : model.textures()) {
							ModelTextures.release(texture);
						}
						released.complete(null);
					};
					//? if >=26.1 {
					/*net.minecraft.client.Minecraft.getInstance().execute(release);
					*///?} else {
					RenderSystem.recordRenderCall(release::run);
					//?}
					released.join();
					ok++;
				} catch (Exception | LinkageError e) {
					entry.addProperty("error", e.toString());
					failed++;
				}
				results.add(entry);
			}
		} catch (IOException e) {
			GemRender.LOGGER.error("import spike: cannot read {}", list, e);
		}
		JsonObject verdict = new JsonObject();
		verdict.addProperty("ok", ok);
		verdict.addProperty("failed", failed);
		verdict.add("models", results);
		try {
			Files.writeString(Path.of(LIST + ".result.json"),
					new GsonBuilder().setPrettyPrinting().create().toJson(verdict));
		} catch (IOException e) {
			GemRender.LOGGER.error("import spike: cannot write result", e);
		}
		GemRender.LOGGER.info("import spike: {} ok, {} failed", ok, failed);
		Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
	}
}
