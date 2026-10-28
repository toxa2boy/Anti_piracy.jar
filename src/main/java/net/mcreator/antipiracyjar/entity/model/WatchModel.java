package net.mcreator.antipiracyjar.entity.model;

import software.bernie.geckolib.model.GeoModel;

import net.minecraft.resources.ResourceLocation;

import net.mcreator.antipiracyjar.entity.WatchEntity;

public class WatchModel extends GeoModel<WatchEntity> {
    @Override
    public void setCustomAnimations(WatchEntity entity, long instanceId,
            software.bernie.geckolib.animation.AnimationState<WatchEntity> state) {
        super.setCustomAnimations(entity,instanceId,state);
        net.mcreator.antipiracyjar.EventHeadTracking.apply(this,entity,state.getPartialTick());
    }

	@Override
	public ResourceLocation getAnimationResource(WatchEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:animations/watch.animation.json");
	}

	@Override
	public ResourceLocation getModelResource(WatchEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:geo/weird-fool.geo.json");
	}

	@Override
	public ResourceLocation getTextureResource(WatchEntity entity) {
		return ResourceLocation.parse("anti_piracyjar:textures/entities/" + entity.getTexture() + ".png");
	}

}
