package com.wf.gemrender.spike;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.Ids;
import com.wf.gemrender.GemRender;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GemRenderPartsModel;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

public final class SpikeAssets {
	public static final ResourceLocation RADAR = Ids.of(
			GemRender.MOD_ID, "models/radar/radar.gltf");

	public static final ResourceLocation RIG = Ids.of(
			GemRender.MOD_ID, "models/rig/rig.glb");

	public static final ResourceLocation MORPH = Ids.of(
			GemRender.MOD_ID, "models/morph/morph.glb");

	public static final ResourceLocation GLASS = Ids.of(
			GemRender.MOD_ID, "models/glass/glass.glb");

	public static final ResourceLocation PBR = Ids.of(
			GemRender.MOD_ID, "models/pbr/pbr.glb");

	public static final ResourceLocation PYLON = Ids.of(
			GemRender.MOD_ID, "models/pylon/pylon.geo.json");

	public static final ResourceLocation PYLON_GLTF = Ids.of(
			GemRender.MOD_ID, "models/pylon/pylon.gltf");

	public static final ResourceLocation RADAR_SKINS = Ids.of(
			GemRender.MOD_ID, "variants/radar");

	private static ResourceLocation skin(String name) {
		return Ids.of(GemRender.MOD_ID, "textures/models/" + name + ".png");
	}

	private static Map<ResourceLocation, ResourceLocation> allOf(String name) {
		ResourceLocation skin = skin(name);
		return Map.of(skin("radar_top"), skin, skin("radar_middle"), skin, skin("radar_bottom"), skin);
	}

	private static final ModelCache.Handle<GemRenderGltfModel> RADAR_VARIANTS =
			GemRenderModels.variants(RADAR_SKINS, RADAR,
					List.of(Map.of(), allOf("radar_bottom"), allOf("radar_top")));

	public static final ResourceLocation PYLON_SKINS = Ids.of(
			GemRender.MOD_ID, "variants/pylon");

	private static final ModelCache.Handle<GemRenderGltfModel> PYLON_VARIANTS =
			GemRenderModels.skins(PYLON_SKINS, PYLON, List.of(
					Ids.of(GemRender.MOD_ID, "models/pylon/pylon.png"),
					skin("pbr_plate"), skin("pbr_lamp")));

	public static ResourceLocation vehicle(String name) {
		return Ids.of(GemRender.MOD_ID,
				"models/vehicles/" + name + "/" + name + ".geo.json");
	}

	private static final ModelCache.Handle<?>[] DECLARED = {
			GemRenderModels.handle(RADAR), GemRenderModels.handle(RIG), GemRenderModels.handle(MORPH),
			GemRenderModels.handle(GLASS), GemRenderModels.handle(PBR), GemRenderModels.handle(PYLON),
			GemRenderModels.handle(PYLON_GLTF) };

	private SpikeAssets() {
	}

	@Nullable
	public static GemRenderGltfModel model(ResourceLocation asset) {

		if (RADAR_SKINS.equals(asset)) {
			return RADAR_VARIANTS.get();
		}
		if (PYLON_SKINS.equals(asset)) {
			return PYLON_VARIANTS.get();
		}
		return GemRenderModels.get(asset);
	}

	@Nullable
	public static GemRenderPartsModel parts(ResourceLocation asset) {
		return GemRenderModels.parts(asset);
	}
}
