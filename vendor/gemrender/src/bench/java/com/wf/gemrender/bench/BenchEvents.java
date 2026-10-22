package com.wf.gemrender.bench;

import com.wf.gemrender.GemRender;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class BenchEvents {
	private BenchEvents() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(BenchClient::setup);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		BenchClient.tick();
	}

	@SubscribeEvent
	public static void onFrameStart(RenderFrameEvent.Pre event) {
		BenchClient.frameStart();
	}

	@SubscribeEvent
	public static void onFrameEnd(RenderFrameEvent.Post event) {
		BenchClient.frameEnd();
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		BenchClient.renderLevel(event.getCamera()
				.getPosition(), event.getModelViewMatrix(),
				event.getPartialTick()
						.getGameTimeDeltaPartialTick(false));
	}
}
