package com.wf.gemrender.spike;

import com.wf.gemrender.Ids;
import com.wf.gemrender.GemRender;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
//? if >=26.1 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?}

public final class SpikeItems {

	public static final ResourceLocation SPIKE = Ids.of(GemRender.MOD_ID,
			"spike");

	private static Item item;

	private SpikeItems() {
	}

	public static Item item() {
		return item;
	}

	public static Item create() {
		item = new SpikeItem(properties());
		return item;
	}

	private static Item.Properties properties() {

		//? if >=26.1 {
		/*return new Item.Properties().stacksTo(1)
				.setId(ResourceKey.create(Registries.ITEM, SPIKE));
*///?} else {
		return new Item.Properties().stacksTo(1);
		//?}
	}
}
