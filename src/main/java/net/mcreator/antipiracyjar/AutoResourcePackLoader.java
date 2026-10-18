package net.mcreator.antipiracyjar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AutoResourcePackLoader {
    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "resourcepacks/classic"),
                PackType.CLIENT_RESOURCES, Component.literal("The Original Work Alpha"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
