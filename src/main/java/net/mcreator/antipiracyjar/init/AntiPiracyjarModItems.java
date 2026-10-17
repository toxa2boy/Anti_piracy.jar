
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.antipiracyjar.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import net.minecraft.world.item.Item;

import net.mcreator.antipiracyjar.AntiPiracyjarMod;

public class AntiPiracyjarModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(AntiPiracyjarMod.MODID);
	public static final DeferredItem<Item> PIRAT_SPAWN_EGG = REGISTRY.register("pirat_spawn_egg", () -> new DeferredSpawnEggItem(AntiPiracyjarModEntities.PIRAT, -1, -1, new Item.Properties()));
	public static final DeferredItem<Item> DRUN_SPAWN_EGG = REGISTRY.register("drun_spawn_egg", () -> new DeferredSpawnEggItem(AntiPiracyjarModEntities.DRUN, -1, -1, new Item.Properties()));
	// Start of user code block custom items
	// End of user code block custom items
}
