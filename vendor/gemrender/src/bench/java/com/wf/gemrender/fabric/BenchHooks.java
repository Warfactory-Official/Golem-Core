package com.wf.gemrender.fabric;

import com.wf.gemrender.bench.BenchClient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class BenchHooks {

	private static boolean setUp;

	private BenchHooks() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BenchHooks::clientTick);
		WorldRenderEvents.START.register(context -> BenchClient.frameStart());
		WorldRenderEvents.END.register(context -> BenchClient.frameEnd());
		WorldRenderEvents.AFTER_ENTITIES.register(BenchHooks::afterEntities);
	}

	private static void clientTick(Minecraft client) {
		if (!setUp) {
			setUp = true;
			BenchClient.setup();
		}
		BenchClient.tick();
	}

	private static void afterEntities(WorldRenderContext context) {
		BenchClient.renderLevel(context.camera()
				.getPosition(), context.positionMatrix(),
				context.tickCounter()
						.getGameTimeDeltaPartialTick(false));
	}
}
