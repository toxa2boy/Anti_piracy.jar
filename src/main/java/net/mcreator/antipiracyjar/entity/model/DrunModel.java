package net.mcreator.antipiracyjar.entity.model;

import software.bernie.geckolib.model.GeoModel;

import net.minecraft.resources.ResourceLocation;

import net.mcreator.antipiracyjar.entity.DrunEntity;

public class DrunModel extends GeoModel<DrunEntity> {
    @Override
    public void setCustomAnimations(DrunEntity entity, long instanceId,
            software.bernie.geckolib.animation.AnimationState<DrunEntity> state) {
        super.setCustomAnimations(entity,instanceId,state);
        net.mcreator.antipiracyjar.EventHeadTracking.apply(this,entity,state.getPartialTick());
    }

	@Override
	public ResourceLocation getAnimationResource(DrunEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:animations/weird-fool.animation.json");
	}

	@Override
	public ResourceLocation getModelResource(DrunEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:geo/weird-fool.geo.json");
	}

	@Override
	public ResourceLocation getTextureResource(DrunEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:textures/entities/" + entity.getTexture() + ".png");
	}

}
