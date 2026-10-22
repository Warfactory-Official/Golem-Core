package com.wf.gemrender.water;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.render.LevelStage;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class WaterSplitEvents {
    private WaterSplitEvents() {
    }

    //? if >=26.1 {
	/*@SubscribeEvent(priority = EventPriority.LOW)
	public static void onAfterEntities(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		WaterSplit.getInstance()
				.onAfterEntities(new LevelStage(event.getLevelRenderState().chunkSectionsToRender));
	}

	@SubscribeEvent
	public static void onAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
		WaterSplit.getInstance()
				.onAfterTranslucent();
	}

	@SubscribeEvent
	public static void onAfterWeather(RenderLevelStageEvent.AfterWeather event) {
		WaterSplit.getInstance()
				.onAfterWeather();
	}
*///?} else {
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            WaterSplit.getInstance()
                    .onAfterEntities(stageOf(event));
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            WaterSplit.getInstance()
                    .onAfterTranslucent();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            WaterSplit.getInstance()
                    .onAfterWeather();
        }
    }
    //?}

    //? if >=1.21 <26.1 {
    private static LevelStage stageOf(RenderLevelStageEvent event) {
        return new LevelStage(event.getLevelRenderer(), event.getCamera(), event.getFrustum(),
                event.getPoseStack(), event.getModelViewMatrix(), event.getProjectionMatrix());
    }
    //?}

    //? if <1.21 {
	/*private static LevelStage stageOf(RenderLevelStageEvent event) {
		return new LevelStage(event.getLevelRenderer(), event.getCamera(), event.getFrustum(),
				event.getPoseStack(), null, event.getProjectionMatrix());
	}
*///?}
}
