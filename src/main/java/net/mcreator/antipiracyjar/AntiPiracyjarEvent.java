package net.mcreator.antipiracyjar;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.mcreator.antipiracyjar.entity.PiratEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class AntiPiracyjarEvent {
    private static final Map<UUID, Boolean> locked = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> pos = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> yaw = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> pitch = new ConcurrentHashMap<>();
    private static final Map<UUID, Entity> spawned = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> sequenceTicks = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> eventTriggered = new ConcurrentHashMap<>();

    private static final long DAYTIME_LIMIT = 12000L;

    @SubscribeEvent
    public static void choose(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 20 != 0) return;
        var world = HorrorWorldState.get(server);
        if (!world.data.getBoolean("messagesFinished") || world.data.getBoolean("pirateDone")) return;
        if (server.overworld().getDayTime() / 24000 < world.data.getLong("messagesFinishedDay") + 1 || !server.overworld().isDay()) return;
        var targets = net.mcreator.antipiracyjar.ClientEventHandler.targets(server);
        if (targets.isEmpty()) return;
        world.data.putBoolean("pirateDone", true);
        for (ServerPlayer player : targets) world.data.putBoolean("pirateSelected_" + player.getUUID(), true);
        world.flush(server);
    }
    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        locked.clear(); pos.clear(); yaw.clear(); pitch.clear(); spawned.clear(); sequenceTicks.clear(); eventTriggered.clear();
    }
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID id = event.getEntity().getUUID();
        locked.put(id, false);
        sequenceTicks.put(id, 0);
        eventTriggered.put(id, false);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        locked.remove(id);
        pos.remove(id);
        yaw.remove(id);
        pitch.remove(id);
        Entity old = spawned.remove(id);
        if (old != null) old.discard();
        sequenceTicks.remove(id);
        eventTriggered.remove(id);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.getPersistentData().getBoolean(FlatLandFinale.DONE)) return;
        if (!player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        UUID id = player.getUUID();
        int seq = sequenceTicks.getOrDefault(id, 0);

        if (seq > 0) {
            seq++;
            sequenceTicks.put(id, seq);
            if (seq % 20 == 0) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(1000 + seq));

            if (seq == 181) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    ServerLevel flatLand = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "flat_land")));
                    if (flatLand != null) {
                        player.teleportTo(flatLand, 0.0, 135.0, 0.0, 0.0F, 0.0F);
                    }
                }
                player.getInventory().clearContent();
                sequenceTicks.put(id, 0);
            }
            return;
        }

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
                    sequenceTicks.put(id, 1);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(1001));
                }
            }
            return;
        }

        if (eventTriggered.getOrDefault(id, false)) return;

        var world = HorrorWorldState.get(player.server);
        if (!world.data.getBoolean("pirateSelected_" + id) || world.data.getBoolean("pirateStarted_" + id)) return;
        ServerLevel level = player.serverLevel();
        {
            world.data.putBoolean("pirateStarted_" + id, true);
            world.flush(player.server);
            eventTriggered.put(id, true);

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

    @EventBusSubscriber(value = Dist.CLIENT)
    public static class ClientEventHandler {
        private static final ResourceLocation PIRAT_IMG = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "textures/screens/piratpng.png");

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            UUID id = mc.player.getUUID();
            int t = HorrorClientEffects.sequence;

            if (t >= 100 && t <= 180) {
                GuiGraphics guiGraphics = event.getGuiGraphics();
                int w = mc.getWindow().getGuiScaledWidth();
                int h = mc.getWindow().getGuiScaledHeight();
                Font font = mc.font;

                if (t >= 100 && t < 170) {
                    String text = "SO PLAY!";
                    int color = (t % 4 < 2) ? 0xFF0000 : 0x550000;
                    float shakeX = (float) (Math.random() - 0.5) * 6.0F;
                    float shakeY = (float) (Math.random() - 0.5) * 6.0F;

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(w / 2.0F + shakeX, h / 2.0F + shakeY, 0);
                    guiGraphics.pose().scale(3.0F, 3.0F, 1.0F);
                    guiGraphics.drawCenteredString(font, text, 0, - (font.lineHeight / 2), color);
                    guiGraphics.pose().popPose();
                }

                if (t >= 130 && t < 175) {
                    String text2 = "SO PLAY!";
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(w / 2.0F + 50, h / 2.0F + 40, 0);
                    guiGraphics.pose().scale(1.5F, 1.5F, 1.0F);
                    guiGraphics.drawCenteredString(font, text2, 0, 0, 0xFFFFFF);
                    guiGraphics.pose().popPose();
                }

                if (t >= 176 && t <= 180) {
                    guiGraphics.blit(PIRAT_IMG, 0, 0, 0, 0, w, h, w, h);
                }
            }
        }
    }
}