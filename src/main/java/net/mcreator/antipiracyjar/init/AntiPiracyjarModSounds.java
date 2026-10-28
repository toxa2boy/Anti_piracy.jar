
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
	public static final DeferredHolder<SoundEvent, SoundEvent> STARTVIDEO = REGISTRY.register("startvideo", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "startvideo")));
	public static final DeferredHolder<SoundEvent, SoundEvent> POMEHIOGG = REGISTRY.register("pomehiogg", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "pomehiogg")));
	public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = REGISTRY.register("ambient", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "ambient")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUN_AMBIENT = REGISTRY.register("drun_ambient", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_ambient")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUN_ATTACK = REGISTRY.register("drun_attack", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_attack")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUN_HURT = REGISTRY.register("drun_hurt", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_hurt")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUN_DEATH = REGISTRY.register("drun_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_death")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DRUN_STEP = REGISTRY.register("drun_step", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_step")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SATANA = REGISTRY.register("satana", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "satana")));
}
