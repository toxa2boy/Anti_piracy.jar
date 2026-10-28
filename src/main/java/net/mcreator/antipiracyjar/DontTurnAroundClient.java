package net.mcreator.antipiracyjar;

import net.mcreator.antipiracyjar.entity.DrunEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class DontTurnAroundClient {
    private static final SoundEvent SCREAM = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_screamer"));
    private static ClientLevel world;
    private static DrunEntity apparition;
    private static SimpleSoundInstance sound;
    private static Vec3 originalLook;
    private static int pending;
    private static int waiting;
    private static int screaming;
    private static int nextId = -2000000;
    private static int frozenTicks;
    private static Vec3 frozenPosition;
    private static Vec3 scareDirection;

    private static void positionAtFace() {
        var player = Minecraft.getInstance().player;
        Vec3 position = player.getEyePosition().add(scareDirection.scale(.8)).add(0, -2.2, 0);
        apparition.noPhysics = true;
        apparition.setNoGravity(true);
        apparition.setDeltaMovement(Vec3.ZERO);
        apparition.moveTo(position.x, position.y, position.z, apparition.getYRot(), 0);
        facePlayer();
    }

    public static void begin() {
        clear();
        frozenTicks = 0;
        frozenPosition = null;
        pending = 100;
    }

    private static boolean visible(Vec3 point) {
        var player = Minecraft.getInstance().player;
        return player != null && world.clip(new ClipContext(player.getEyePosition(), point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static Vec3 findPosition(Vec3 forward) {
        var player = Minecraft.getInstance().player;
        for (double distance : new double[]{2.8, 3.5, 2.0, 4.0}) {
            for (float angle : new float[]{0, 20, -20, 40, -40}) {
                Vec3 behind = forward.yRot(angle * Mth.DEG_TO_RAD).scale(-distance);
                for (double height : new double[]{0, -1, 1, -2, 2}) {
                    Vec3 candidate = player.position().add(behind.x, height, behind.z);
                    AABB box = new AABB(candidate.x - .4, candidate.y, candidate.z - .4,
                            candidate.x + .4, candidate.y + 2.8, candidate.z + .4);
                    if (world.noCollision(box) && !world.noCollision(box.move(0, -.15, 0))
                            && visible(candidate.add(0, 1.6, 0))) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean spawn() {
        var mc = Minecraft.getInstance();
        world = mc.level;
        originalLook = Vec3.directionFromRotation(0, mc.player.getYRot());
        Vec3 position = findPosition(originalLook);
        if (position == null) return false;
        apparition = AntiPiracyjarModEntities.DRUN.get().create(world);
        if (apparition == null) return false;
        while (world.getEntity(nextId) != null) nextId--;
        apparition.setId(nextId--);
        apparition.setNoAi(true);
        apparition.setNoGravity(true);
        apparition.setSilent(true);
        apparition.setInvulnerable(true);
        apparition.noPhysics = true;
        apparition.moveTo(position.x, position.y, position.z, mc.player.getYRot(), 0);
        facePlayer();
        apparition.setEventAnimation("event_watch");
        world.addEntity(apparition);
        if (world.getEntity(apparition.getId()) != apparition) {
            apparition = null;
            return false;
        }
        mc.gui.getChat().addMessage(Component.literal("Don't turn around."));
        waiting = 300;
        pending = 0;
        return true;
    }

    private static void facePlayer() {
        var player = Minecraft.getInstance().player;
        float yaw = (float)(Mth.atan2(player.getZ() - apparition.getZ(), player.getX() - apparition.getX())
                * Mth.RAD_TO_DEG) - 90;
        apparition.setYRot(yaw);
        apparition.setYBodyRot(yaw);
        apparition.setYHeadRot(yaw);
        apparition.yRotO = yaw;
        apparition.yBodyRotO = yaw;
        apparition.yHeadRotO = yaw;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (frozenTicks > 0 && !mc.isPaused()) {
            if (mc.player == null || !mc.player.isAlive() || mc.level == null
                    || mc.level.dimension() != Level.OVERWORLD) {
                frozenTicks = 0;
                frozenPosition = null;
            } else {
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(frozenPosition);
                mc.player.setSprinting(false);
                frozenTicks--;
            }
        }
        if (pending == 0 && apparition == null) return;
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.level.dimension() != Level.OVERWORLD || (apparition != null && mc.level != world)) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        if (pending > 0) {
            pending--;
            if (mc.screen != null) {
                if (pending == 0) clear();
                return;
            }
            if (!spawn() && pending == 0) clear();
            return;
        }
        if (apparition.isRemoved() || mc.player.distanceToSqr(apparition) > 400) {
            clear();
            return;
        }
        facePlayer();
        apparition.setDeltaMovement(Vec3.ZERO);
        if (screaming > 0) {
            positionAtFace();
            if (--screaming == 0) clear();
            return;
        }
        if (--waiting <= 0) {
            clear();
            return;
        }
        if (mc.screen != null) return;
        Vec3 look = mc.player.getLookAngle();
        Vec3 target = apparition.position().add(0, 1.65, 0);
        Vec3 direction = target.subtract(mc.player.getEyePosition()).normalize();
        if (look.dot(direction) > .65 && visible(target)) {
            scareDirection = Vec3.directionFromRotation(0, mc.player.getYRot());
            positionAtFace();
            apparition.setEventAnimation("event_screamer");
            screaming = 52;
            frozenTicks = 60;
            frozenPosition = mc.player.position();
            PacketDistributor.sendToServer(new HorrorNetwork.ScarePayload(true));
            sound = SimpleSoundInstance.forUI(SCREAM, 1F, 1F);
            mc.getSoundManager().play(sound);
            return;
        }
        Vec3 behind = findPosition(Vec3.directionFromRotation(0, mc.player.getYRot()));
        if (behind != null) {
            Vec3 delta = behind.subtract(apparition.position());
            double distance = delta.length();
            if (distance > .12) {
                Vec3 step = delta.scale(Math.min(.27, distance) / distance);
                if (world.noCollision(apparition.getBoundingBox().move(step))) {
                    apparition.setPos(apparition.position().add(step));
                    apparition.setEventAnimation("event_follow");
                } else apparition.setEventAnimation("event_watch");
            } else apparition.setEventAnimation("event_watch");
        }
    }

    public static void clear() {
        if (world != null && apparition != null && world.getEntity(apparition.getId()) == apparition)
            world.removeEntity(apparition.getId(), Entity.RemovalReason.DISCARDED);
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        apparition = null;
        world = null;
        sound = null;
        originalLook = null;
        scareDirection = null;
        pending = waiting = screaming = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        frozenTicks = 0;
        frozenPosition = null;
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (frozenTicks <= 0) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
