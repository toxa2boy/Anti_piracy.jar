package net.mcreator.antipiracyjar.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.mcreator.antipiracyjar.entity.PiratEntity;
import net.mcreator.antipiracyjar.client.model.Modelpirat;

public class PiratRenderer extends MobRenderer<PiratEntity, Modelpirat<PiratEntity>> {

    public PiratRenderer(EntityRendererProvider.Context context) {
        super(context, new Modelpirat<>(context.bakeLayer(Modelpirat.LAYER_LOCATION)), 0.0F);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(PiratEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "textures/entities/porno.png");
    }

    @Override
    public float getShadowRadius(PiratEntity entity) {
        return 0.0F;
    }
}