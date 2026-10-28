package net.mcreator.antipiracyjar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DrunVisitPath {
    private record Node(BlockPos pos, Node parent, int depth) {}

    public static boolean clear(Level level, Vec3 eye, Vec3 point) {
        int steps = Math.max(1, (int)Math.ceil(eye.distanceTo(point) * 5));
        for (int i = 1; i < steps; i++) {
            Vec3 sample = eye.lerp(point, i / (double)steps);
            BlockPos pos = BlockPos.containing(sample);
            if (!level.hasChunkAt(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                    || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)) continue;
            Vec3 local = sample.subtract(pos.getX(), pos.getY(), pos.getZ());
            for (AABB box : state.getCollisionShape(level,pos).toAabbs()) if (box.contains(local)) return false;
        }
        return true;
    }

    public static boolean hidden(Level level, Vec3 feet) {
        for (Player player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(feet) > 64 * 64) continue;
            Vec3 eye = player.getEyePosition().add(0,player.isSleeping() ? 1.2 : 0,0);
            if (clear(level,eye,feet.add(0,2.375,0)) || clear(level,eye,feet.add(0,1.2,0))) return false;
        }
        return true;
    }

    public static boolean free(Level level, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        return level.hasChunkAt(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                && level.noCollision(new AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+2.7,feet.z+.3));
    }

    public static List<BlockPos> find(Level level, BlockPos target) {
        var queue = new ArrayDeque<Node>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(new Node(target,null,0)); seen.add(target);
        int budget = 256;
        while (!queue.isEmpty() && budget-- > 0) {
            Node node = queue.removeFirst();
            if (node.depth() >= 4 && hidden(level,Vec3.atBottomCenterOf(node.pos()))) {
                List<BlockPos> result = new ArrayList<>();
                for (Node n=node; n!=null; n=n.parent()) result.add(n.pos());
                return result;
            }
            if (node.depth() >= 12) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = node.pos().relative(direction);
                if (!seen.add(next) || !free(level,Vec3.atBottomCenterOf(next))) continue;
                if (!free(level,Vec3.atBottomCenterOf(node.pos()).lerp(Vec3.atBottomCenterOf(next),.5))) continue;
                queue.add(new Node(next,node,node.depth()+1));
            }
        }
        return List.of();
    }
}
