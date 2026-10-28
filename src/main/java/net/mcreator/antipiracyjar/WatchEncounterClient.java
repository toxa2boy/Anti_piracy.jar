package net.mcreator.antipiracyjar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.mcreator.antipiracyjar.entity.WatchEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class WatchEncounterClient {
    private static ClientLevel world;
    private static WatchEntity watch;
    private static int nextId = -3000000;
    private static int phase;
    private static int age;
    private static int unseen;
    private static int pathAge;
    private static int heartbeat;
    private static List<Vec3> route = List.of();
    private static int routeIndex;
    private record Node(Vec3 position, Node parent, int depth) {}

    private static boolean clearRay(Vec3 target) {
        var player = Minecraft.getInstance().player;
        return world.clip(new ClipContext(player.getEyePosition(), target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static boolean visible(Vec3 feet) {
        return clearRay(feet.add(0, 2.2, 0)) || clearRay(feet.add(0, 1.25, 0));
    }

    private static AABB bounds(Vec3 feet) {
        return new AABB(feet.x - .35, feet.y, feet.z - .35, feet.x + .35, feet.y + 2.75, feet.z + .35);
    }

    private static Vec3 floor(int x, int z, double y, int search) {
        for (int offset = 0; offset <= search; offset++) {
            for (int sign : new int[]{1, -1}) {
                int by = Mth.floor(y) + offset * sign;
                var ground = new BlockPos(x, by - 1, z);
                if (by < world.getMinBuildHeight() + 1 || by + 3 >= world.getMaxBuildHeight()
                        || !world.hasChunkAt(ground)) continue;
                var shape = world.getBlockState(ground).getCollisionShape(world, ground);
                if (shape.isEmpty() || !world.getFluidState(ground).isEmpty()) continue;
                Vec3 position = new Vec3(x + .5, by - 1 + shape.max(net.minecraft.core.Direction.Axis.Y), z + .5);
                if (world.noCollision(watch, bounds(position)) && world.getFluidState(BlockPos.containing(position)).isEmpty())
                    return position;
            }
        }
        return null;
    }

    private static double viewDot(Vec3 feet) {
        var player = Minecraft.getInstance().player;
        return player.getLookAngle().dot(feet.add(0, 2.2, 0).subtract(player.getEyePosition()).normalize());
    }

    private static void report(int action) {
        if (Minecraft.getInstance().getConnection() != null)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HorrorNetwork.WatchStatePayload(action));
    }

    public static void scare() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var sound = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_screamer"));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1F, 1F));
        float yaw = mc.player.getYRot() + (mc.level.random.nextBoolean() ? 35 : -35);
        float pitch = Mth.clamp(mc.player.getXRot() - 22, -80, 80);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    public static void test() {
        begin();
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                watch != null ? "Watch spawned. Look ahead; approach within 6 blocks to test the scare."
                : "Watch could not spawn: face open ground 10-28 blocks away with room to walk. Close menus and try again."), false);
    }

    public static void begin() {
        var mc = Minecraft.getInstance();
        if (watch != null || mc.level == null || mc.player == null || mc.screen != null
                || !mc.player.isAlive() || mc.player.isSpectator() || mc.level.dimension() != Level.OVERWORLD) { report(0); return; }
        world = mc.level;
        boolean cave = !world.canSeeSky(mc.player.blockPosition());
        Vec3 spawn = null;
        double threshold = Math.cos(Math.toRadians(Math.min(45, mc.options.fov().get() * .40)));
        for (int attempt = 0; attempt < 64 && spawn == null; attempt++) {
            double distance = cave ? 10.5 + world.random.nextDouble() * 5.5 : 14 + world.random.nextDouble() * 14;
            float yaw = mc.player.getYRot() + (world.random.nextFloat() - .5F) * 80;
            Vec3 offset = Vec3.directionFromRotation(0, yaw).scale(distance);
            Vec3 candidate = floor(Mth.floor(mc.player.getX() + offset.x), Mth.floor(mc.player.getZ() + offset.z),
                    mc.player.getY(), cave ? 3 : 7);
            if (candidate != null && candidate.distanceToSqr(mc.player.position()) >= 100
                    && candidate.distanceToSqr(mc.player.position()) <= 900
                    && viewDot(candidate) > threshold && clearRay(candidate.add(0, 2.2, 0))) {
                List<Vec3> exit = escapeRoute(candidate);
                if (!exit.isEmpty()) { spawn = candidate; route = exit; }
            }
        }
        if (spawn == null) { world = null; report(0); return; }
        watch = AntiPiracyjarModEntities.WATCH.get().create(world);
        if (watch == null) { world = null; report(0); return; }
        while (world.getEntity(nextId) != null) nextId--;
        watch.setId(nextId--);
        watch.setNoAi(true);
        watch.setNoGravity(true);
        watch.setSilent(true);
        watch.setInvulnerable(true);
        watch.noPhysics = true;
        watch.moveTo(spawn.x, spawn.y, spawn.z, 0, 0);
        watch.setWatchAnimation("watch_wait");
        face(mc.player.position());
        world.addEntity(watch);
        if (world.getEntity(watch.getId()) != watch) { clear(); return; }
        phase = age = unseen = pathAge = routeIndex = heartbeat = 0;
        report(1);
    }

    private static void face(Vec3 target) {
        float yaw = (float)(Mth.atan2(target.z - watch.getZ(), target.x - watch.getX()) * Mth.RAD_TO_DEG) - 90;
        watch.setYRot(yaw);
        watch.setYBodyRot(yaw);
        watch.setYHeadRot(yaw);
        watch.yRotO = watch.yBodyRotO = watch.yHeadRotO = yaw;
    }

    private static boolean walkable(Vec3 from, Vec3 to) {
        if (Math.abs(to.y - from.y) > 1.05) return false;
        double top = Math.max(from.y, to.y);
        for (int i = 1; i <= 4; i++) {
            Vec3 point = from.lerp(to, i / 4.0);
            if (!world.noCollision(watch, bounds(new Vec3(point.x, top, point.z)))) return false;
        }
        return true;
    }

    private static List<Vec3> escapeRoute(Vec3 origin) {
        var player = Minecraft.getInstance().player;
        var start = new Node(origin, null, 0);
        var queue = new ArrayDeque<Node>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(BlockPos.containing(start.position()));
        Node best = null;
        double bestScore = -Double.MAX_VALUE;
        int checked = 0;
        while (!queue.isEmpty() && checked++ < 320) {
            Node node = queue.removeFirst();
            double distance = node.position().distanceTo(player.position());
            if (node.depth() >= 3 && distance >= 8) {
                boolean hidden = !visible(node.position());
                double score = (hidden ? 100 : 0) + (viewDot(node.position()) < .2 ? 25 : 0)
                        + distance * .6 - node.depth() * .15;
                if (score > bestScore) { bestScore = score; best = node; }
                if (hidden) break;
            }
            if (node.depth() >= 16) continue;
            int x = Mth.floor(node.position().x), z = Mth.floor(node.position().z);
            for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Vec3 next = floor(x + direction[0], z + direction[1], node.position().y, 1);
                if (next == null || next.distanceToSqr(player.position()) < 49) continue;
                BlockPos key = BlockPos.containing(next);
                if (visited.contains(key) || !walkable(node.position(), next)) continue;
                visited.add(key);
                queue.addLast(new Node(next, node, node.depth() + 1));
            }
        }
        if (best == null) return List.of();
        List<Vec3> result = new ArrayList<>();
        for (Node node = best; node.parent() != null; node = node.parent()) result.add(node.position());
        Collections.reverse(result);
        return result;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (watch == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()
                || watch.isRemoved()) { clear(); return; }
        if (mc.isPaused()) return;
        watch.setDeltaMovement(Vec3.ZERO);
        age++;
        if (++heartbeat >= 200) { heartbeat = 0; report(1); }
        double distance = mc.player.distanceToSqr(watch);
        if (distance < 36) { report(3); clear(); return; }
        if (distance > 6400) { clear(); return; }
        if (phase < 3) face(mc.player.position());
        if (phase == 0) {
            if (mc.screen == null && viewDot(watch.position()) > .992 && clearRay(watch.position().add(0, 2.2, 0))) {
                watch.setWatchAnimation("watch_wave");
                phase = 1; age = 0;
            } else if (age >= 400) clear();
            return;
        }
        if (phase == 1) {
            if (age >= 60) { phase = 2; age = 0; watch.setWatchAnimation("watch_wait"); }
            return;
        }
        if (phase == 2) {
            if (age >= 60) { phase = 3; age = 0; pathAge = 20; }
            return;
        }
        
        if (!visible(watch.position()) || viewDot(watch.position()) < -.1) unseen++; else unseen = 0;
        if (unseen >= 8) { clear(); return; }
        if (routeIndex >= route.size() || pathAge >= 20) {
            if (++pathAge < 20) return;
            route = escapeRoute(watch.position()); routeIndex = 0; pathAge = 0;
            if (route.isEmpty()) { watch.setWatchAnimation("watch_wait"); return; }
        }
        Vec3 target = route.get(routeIndex);
        Vec3 current = watch.position();
        double speed = Math.min(1.2, Math.max(.45, mc.player.getDeltaMovement().horizontalDistance() * 1.35 + .15));
        Vec3 delta = new Vec3(target.x - current.x, 0, target.z - current.z);
        double horizontal = delta.length();
        Vec3 next;
        if (target.y > current.y + .01) {
            next = current.add(0, Math.min(.25, target.y - current.y), 0);
        } else if (horizontal > .001) {
            next = current.add(delta.scale(Math.min(speed, horizontal) / horizontal));
        } else {
            next = new Vec3(target.x, Math.max(target.y, current.y - .25), target.z);
        }
        boolean free = true;
        for (int i = 1; i <= 4; i++) {
            if (!world.noCollision(watch, bounds(current.lerp(next, i / 4.0)))) { free = false; break; }
        }
        if (!free) {
            watch.setWatchAnimation("watch_wait");
            route = List.of(); pathAge = 0;
            return;
        }
        watch.setWatchAnimation("watch_walk");
        if (horizontal > .001) face(target);
        watch.setPos(next);
        if (next.distanceToSqr(target) < .0001) {
            routeIndex++;
            if (routeIndex >= route.size()) { pathAge = 20; watch.setWatchAnimation("watch_wait"); }
        }
    }

    private static void clear() {
        if (watch != null) report(2);
        if (world != null && watch != null && world.getEntity(watch.getId()) == watch)
            world.removeEntity(watch.getId(), Entity.RemovalReason.DISCARDED);
        watch = null; world = null; route = List.of(); routeIndex = 0;
        phase = age = unseen = pathAge = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
