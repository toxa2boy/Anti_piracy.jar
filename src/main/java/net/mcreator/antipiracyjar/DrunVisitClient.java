package net.mcreator.antipiracyjar;

import net.mcreator.antipiracyjar.entity.DrunEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class DrunVisitClient {
    private static Screen previous;
    private static ClientLevel world;
    private static DrunEntity corner;
    private static DrunEntity visitor;
    private static int life;
    private static java.util.List<BlockPos> route = java.util.List.of();
    private static int waypoint;
    private static int phase;
    private static int pause;
    private static BlockPos knockTarget;
    private static int nextId = -4000000;
    private static boolean force;
    private static int uiHeartbeat;
    private static boolean uiPending;

    private static void reportUi(int action) {
        if (Minecraft.getInstance().getConnection()!=null)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HorrorNetwork.UiVisitPayload(action));
    }

    public static void allowCorner() {
        var mc=Minecraft.getInstance();
        if (!uiPending || !(mc.screen instanceof AbstractContainerScreen<?>) || mc.player==null || mc.level!=world) { reportUi(0); return; }
        uiPending=false;
        spawnCorner(false);
        if (corner==null) reportUi(0); else { uiHeartbeat=0; reportUi(3); }
    }

    public static void testCorner() {
        force = true;
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("Open your inventory or a container to test Drun."), false);
    }

    private static DrunEntity create(Vec3 feet, Vec3 target, String animation) {
        var entity = AntiPiracyjarModEntities.DRUN.get().create(world);
        if (entity == null) return null;
        while (world.getEntity(nextId) != null) nextId--;
        entity.setId(nextId--);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.noPhysics = true;
        float yaw = (float)(Mth.atan2(target.z - feet.z, target.x - feet.x) * Mth.RAD_TO_DEG) - 90;
        entity.moveTo(feet.x, feet.y, feet.z, yaw, 0);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yRotO = entity.yBodyRotO = entity.yHeadRotO = yaw;
        entity.setEventAnimation(animation);
        world.addEntity(entity);
        return world.getEntity(entity.getId()) == entity ? entity : null;
    }

    private static void spawnCorner(boolean test) {
        var mc = Minecraft.getInstance();
        Vec3 best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < 48; attempt++) {
            float angle = (world.random.nextBoolean() ? 1 : -1) * (24 + world.random.nextFloat() * 23);
            Vec3 offset = Vec3.directionFromRotation(0, mc.player.getYRot() + angle).scale(3 + world.random.nextDouble() * 5);
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos pos = BlockPos.containing(mc.player.position().add(offset.x, dy, offset.z));
                if (!world.hasChunkAt(pos) || !world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP)) continue;
                Vec3 feet = Vec3.atBottomCenterOf(pos);
                if (!world.noCollision(new AABB(feet.x-.35, feet.y, feet.z-.35, feet.x+.35, feet.y+2.7, feet.z+.35))) continue;
                if (world.clip(new ClipContext(mc.player.getEyePosition(), feet.add(0,2.2,0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
                int score = 0;
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                    if (!world.getBlockState(pos.relative(direction).above()).isAir()) score++;
                if (score > bestScore) { bestScore = score; best = feet; }
            }
        }
        if (best != null) corner = create(best, mc.player.position(), "event_ui");
        if (test && corner == null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("No visible free place nearby. Try beside an open room or clearing."), false);
    }

    public static void knock(HorrorNetwork.KnockPayload packet) {
        var mc = Minecraft.getInstance();
        if (packet.stop()) { remove(visitor); visitor = null; return; }
        if (mc.level == null || mc.player == null) return;
        if (world != mc.level) clear();
        world = mc.level;
        remove(visitor);
        route = packet.route();
        if (route.size() < 2) { report(2); return; }
        if (!DrunVisitPath.hidden(world,Vec3.atBottomCenterOf(route.getFirst()))) { report(2); return; }
        knockTarget = packet.target();
        visitor = create(Vec3.atBottomCenterOf(route.getFirst()),Vec3.atBottomCenterOf(route.get(1)),"event_visit_walk");
        waypoint = 1; phase = 0; pause = 0;
        if (visitor == null) report(2);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) { clear(); return; }
        if (world != mc.level) { clear(); world = mc.level; }
        if (mc.screen != previous) {
            if (corner!=null || uiPending) reportUi(corner!=null ? 2 : 0);
            remove(corner); corner = null; uiPending=false;
            if (mc.screen instanceof AbstractContainerScreen<?> && !mc.player.isSpectator() && !mc.player.isSleeping()) {
                boolean test = force;
                force = false;
                if (test || world.random.nextInt(10) == 0) { uiPending=true; reportUi(test ? 4 : 1); }
            }
            previous = mc.screen;
        }
        if (corner!=null && ++uiHeartbeat>=20) { uiHeartbeat=0; reportUi(3); }
        if (mc.isPaused()) return;
        if (visitor != null) moveVisitor();
    }

    private static void report(int action) {
        if (Minecraft.getInstance().getConnection() != null)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HorrorNetwork.KnockStatePayload(action));
    }

    private static void faceVisitor(Vec3 point) {
        float yaw = (float)(Mth.atan2(point.z-visitor.getZ(),point.x-visitor.getX())*Mth.RAD_TO_DEG)-90;
        visitor.setYRot(yaw); visitor.setYBodyRot(yaw); visitor.setYHeadRot(yaw);
    }

    private static void moveVisitor() {
        if (phase == 1) {
            if (++pause >= 132) { phase=2; waypoint=route.size()-2; visitor.setEventAnimation("event_visit_walk"); }
            else if (pause == 72) visitor.setEventAnimation("event_visit_wait");
            return;
        }
        if (waypoint < 0) {
            if (DrunVisitPath.hidden(world,visitor.position())) { remove(visitor); visitor=null; report(2); }
            else visitor.setEventAnimation("event_visit_wait");
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(route.get(waypoint));
        Vec3 delta = target.subtract(visitor.position());
        double distance = delta.length();
        double speed = phase == 2 ? .28 : .2;
        Vec3 next = distance <= speed ? target : visitor.position().add(delta.scale(speed/distance));
        if (!DrunVisitPath.free(world,next)) {
            visitor.setEventAnimation("event_visit_wait");
            if (DrunVisitPath.hidden(world,visitor.position())) { remove(visitor); visitor=null; report(2); }
            return;
        }
        visitor.setEventAnimation("event_visit_walk");
        if (distance > .001) faceVisitor(target);
        visitor.setPos(next);
        if (distance <= speed + .00001) {
            waypoint += phase == 0 ? 1 : -1;
            if (phase == 0 && waypoint >= route.size()) {
                phase=1; pause=0;
                faceVisitor(Vec3.atCenterOf(knockTarget));
                visitor.setEventAnimation("event_knock");
                report(1);
            }
        }
    }

    private static void remove(DrunEntity entity) {
        if (entity != null && world != null && world.getEntity(entity.getId()) == entity)
            world.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
    }

    private static void clear() {
        if (corner!=null || uiPending) reportUi(corner!=null ? 2 : 0);
        uiPending=false;
        remove(corner); remove(visitor);
        corner = visitor = null; previous = null; world = null; life = 0; force = false;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
