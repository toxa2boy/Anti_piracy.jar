package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.model.GeoModel;

public final class EventHeadTracking {
    public static void apply(GeoModel<?> model, LivingEntity entity, float partialTick) {
        if (entity.getId()>=0) return;
        var player=Minecraft.getInstance().player;
        var head=model.getAnimationProcessor().getBone("Head");
        if (player==null || head==null || player.level()!=entity.level()) return;
        var origin=entity.getPosition(partialTick).add(0,2.375,0);
        var delta=player.getEyePosition(partialTick).subtract(origin);
        float yaw=(float)(Mth.atan2(delta.z,delta.x)*Mth.RAD_TO_DEG)-90;
        float body=Mth.rotLerp(partialTick,entity.yBodyRotO,entity.yBodyRot);
        float relative=Mth.wrapDegrees(yaw-body);
        float weight=1-Mth.clamp((Math.abs(relative)-85)/50,0,1);
        float pitch=(float)(Mth.atan2(delta.y,Math.max(.1,delta.horizontalDistance()))*Mth.RAD_TO_DEG);
        head.setRotY(head.getRotY()-Mth.clamp(relative,-65,65)*Mth.DEG_TO_RAD*weight);
        head.setRotX(head.getRotX()+Mth.clamp(pitch,-35,35)*Mth.DEG_TO_RAD*weight);
    }
}
