
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.antipiracyjar.init;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.mcreator.antipiracyjar.client.renderer.PiratRenderer;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AntiPiracyjarModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(AntiPiracyjarModEntities.PIRAT.get(), PiratRenderer::new);
	}
}
