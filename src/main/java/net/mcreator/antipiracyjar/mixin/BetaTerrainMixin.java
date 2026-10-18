package net.mcreator.antipiracyjar.mixin;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JigsawStructure.class)
public abstract class BetaTerrainMixin {
    @Shadow @Final private Holder<StructureTemplatePool> startPool;

    @Inject(method = "findGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void anti_piracyjar$fitTerrain(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> callback) {
        var key = startPool.unwrapKey();
        if (key.isEmpty()) return;
        var id = key.get().location();
        if (!id.getNamespace().equals("anti_piracyjar") || !id.getPath().startsWith("beta_")) return;
        var result = callback.getReturnValue();
        if (result.isEmpty()) return;
        var builder = result.get().getPiecesBuilder();
        if (builder.isEmpty()) return;
        var bounds = builder.getBoundingBox();
        int[] heights = new int[16];
        int index = 0;
        for (int ix = 0; ix < 4; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                int x = bounds.minX() + (bounds.maxX() - bounds.minX()) * ix / 3;
                int z = bounds.minZ() + (bounds.maxZ() - bounds.minZ()) * iz / 3;
                heights[index++] = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        context.heightAccessor(), context.randomState());
            }
        }
        Arrays.sort(heights);
        if (heights[15] - heights[0] > 4 || heights[0] < context.chunkGenerator().getSeaLevel()) {
            callback.setReturnValue(Optional.empty());
            return;
        }
        int floor = heights[8] - 1;
        builder.offsetPiecesVertically(floor - 3 - bounds.minY());
        BlockPos anchor = new BlockPos(result.get().position().getX(), floor + 1, result.get().position().getZ());
        callback.setReturnValue(Optional.of(new Structure.GenerationStub(anchor,
                target -> builder.build().pieces().forEach(target::addPiece))));
    }
}
