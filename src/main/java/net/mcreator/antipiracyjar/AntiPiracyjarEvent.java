package net.mcreator.antipiracyjar;

import net.mcreator.antipiracyjar.entity.PiratEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber
public class AntiPiracyjarEvent {
private static final Map<UUID, Integer> ticks = new HashMap<>();
private static final Map<UUID, Boolean> locked = new HashMap<>();
private static final Map<UUID, Vec3> pos = new HashMap<>();
private static final Map<UUID, Float> yaw = new HashMap<>();
private static final Map<UUID, Float> pitch = new HashMap<>();
private static final Map<UUID, Entity> spawned = new HashMap<>();

@SubscribeEvent
public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    ticks.put(event.getEntity().getUUID(), 0);
    locked.put(event.getEntity().getUUID(), false);
}

@SubscribeEvent
public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
    UUID id = event.getEntity().getUUID();
    ticks.remove(id);
    locked.remove(id);
    pos.remove(id);
    yaw.remove(id);
    pitch.remove(id);
    spawned.remove(id);
}

@SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
        return;
    }

    UUID id = player.getUUID();
    boolean isLocked = locked.getOrDefault(id, false);

    if (isLocked) {
        Vec3 p = pos.get(id);
        if (p != null) {
            player.connection.teleport(p.x, p.y, p.z, yaw.get(id), pitch.get(id));
        }

        Entity pirate = spawned.get(id);
        if (pirate == null || !pirate.isAlive()) {
            locked.put(id, false);
            spawned.remove(id);
        } else {
            Vec3 dir = new Vec3(player.getX() - pirate.getX(), (player.getY() + player.getEyeHeight() / 2) - (pirate.getY() + pirate.getEyeHeight() / 2), player.getZ() - pirate.getZ()).normalize();
            
            pirate.setPos(pirate.getX() + dir.x * 0.15, pirate.getY() + dir.y * 0.15, pirate.getZ() + dir.z * 0.15);
            
            pirate.setYRot((float) (Mth.atan2(dir.z, dir.x) * (180F / Math.PI)) - 90.0F);
            pirate.setXRot((float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180F / Math.PI))));

            if (pirate.distanceTo(player) <= 2.0F) {
                pirate.discard();
                locked.put(id, false);
                spawned.remove(id);
            }
        }
        return;
    }

    int t = ticks.getOrDefault(id, 0);
    if (t < 300) {
        ticks.put(id, t + 1);
        if (t + 1 == 300) {
            ServerLevel level = (ServerLevel) player.level();
            Vec3 look = player.getLookAngle();

            double x = player.getX() + look.x * 20.0;
            double z = player.getZ() + look.z * 20.0;
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);

            Entity entity = AntiPiracyjarModEntities.PIRAT.get().create(level);

            if (entity instanceof PiratEntity pirat) {
                pirat.setPos(x, y, z);
                pirat.noPhysics = true;
                pirat.setNoGravity(true);
                
                level.addFreshEntity(pirat);
                
                level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 1.0F);

                locked.put(id, true);
                pos.put(id, player.position());
                yaw.put(id, player.getYRot());
                pitch.put(id, player.getXRot());
                spawned.put(id, pirat);
            }
        }
    }
}
}