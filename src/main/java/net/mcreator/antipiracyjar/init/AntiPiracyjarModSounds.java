
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.antipiracyjar.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.antipiracyjar.AntiPiracyjarMod;

public class AntiPiracyjarModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, AntiPiracyjarMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> VHS = REGISTRY.register("vhs", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "vhs")));
	public static final DeferredHolder<SoundEvent, SoundEvent> STUK_V_DVER = REGISTRY.register("stuk-v-dver", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "stuk-v-dver")));
}
