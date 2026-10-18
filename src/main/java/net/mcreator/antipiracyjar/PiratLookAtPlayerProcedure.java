package net.mcreator.antipiracyjar.procedures;

import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;

import net.mcreator.antipiracyjar.entity.PiratEntity;

@EventBusSubscriber
public class PiratLookAtPlayerProcedure {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof PiratEntity entity) {
            Player player = entity.level().getNearestPlayer(entity, 128.0D);
            if (player != null) {
                double d0 = player.getX() - entity.getX();
                double d1 = player.getZ() - entity.getZ();
                float f = (float) (Math.atan2(d1, d0) * (180.0 / Math.PI)) - 90.0F;

                entity.setYRot(f);
                entity.setYBodyRot(f);
                entity.setYHeadRot(f);
                entity.yRotO = f;
                entity.yBodyRotO = f;
                entity.yHeadRotO = f;

                double d2 = player.getEyeY() - entity.getEyeY();
                double d3 = Math.sqrt(d0 * d0 + d1 * d1);
                float f1 = (float) (-(Math.atan2(d2, d3) * (180.0 / Math.PI)));

                entity.setXRot(f1);
                entity.xRotO = f1;
            }
        }
    }
}