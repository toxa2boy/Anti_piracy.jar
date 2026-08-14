
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.antipiracyjar.init;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.antipiracyjar.client.model.Modelpirat;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public class AntiPiracyjarModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(Modelpirat.LAYER_LOCATION, Modelpirat::createBodyLayer);
	}
}
