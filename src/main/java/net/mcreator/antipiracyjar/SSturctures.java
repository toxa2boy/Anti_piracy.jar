package net.mcreator.antipiracyjar;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.Optional;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class SSturctures {

    public SSturctures() {
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void clientLoad(FMLClientSetupEvent event) {
    }

    @EventBusSubscriber
    private static class SSturcturesForgeBusEvents {

        @SubscribeEvent
        public static void serverLoad(ServerStartingEvent event) {
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }

            if (!player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
            ServerLevel level = player.server.overworld();
            StructureWorldData data = StructureWorldData.get(level);

            if (data.isGenerated()) {
                return;
            }

            RandomSource random = level.getRandom();

            double angle1 = random.nextDouble() * Math.PI * 2.0;
            double dist1 = 300.0 + random.nextDouble() * 700.0;
            int x1 = (int) Math.floor(player.getX() + Math.cos(angle1) * dist1);
            int y1 = 120 + random.nextInt(81);
            int z1 = (int) Math.floor(player.getZ() + Math.sin(angle1) * dist1);
            BlockPos posHouse = new BlockPos(x1, y1, z1);

            double angle2 = random.nextDouble() * Math.PI * 2.0;
            double dist2 = 300.0 + random.nextDouble() * 700.0;
            int x2 = (int) Math.floor(player.getX() + Math.cos(angle2) * dist2);
            int y2 = 120 + random.nextInt(81);
            int z2 = (int) Math.floor(player.getZ() + Math.sin(angle2) * dist2);
            BlockPos posStair = new BlockPos(x2, y2, z2);

            boolean successHouse = spawnNbtStructure(level, "shouse", posHouse, random);
            boolean successStair = spawnNbtStructure(level, "sstair", posStair, random);

            if (successHouse || successStair) {
                data.setGenerated(true);
            }
        }

        private static boolean spawnNbtStructure(ServerLevel level, String structureName, BlockPos pos, RandomSource random) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", structureName);
            var manager = level.getStructureManager();
            Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> templateOpt = manager.get(location);

            if (templateOpt.isEmpty()) {
                return false;
            }

            var template = templateOpt.get();
            Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
            var placeSettings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                    .setRotation(rotation)
                    .setIgnoreEntities(false);

            template.placeInWorld(level, pos, pos, placeSettings, random, 2);
            return true;
        }
    }

    private static class StructureWorldData extends net.minecraft.world.level.saveddata.SavedData {
        private static final String DATA_NAME = "anti_piracyjar_structures_generated";
        private boolean generated = false;

        public StructureWorldData() {
        }

        public boolean isGenerated() {
            return generated;
        }

        public void setGenerated(boolean generated) {
            this.generated = generated;
            this.setDirty();
        }

        public static StructureWorldData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            StructureWorldData data = new StructureWorldData();
            data.generated = tag.getBoolean("generated");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
            tag.putBoolean("generated", this.generated);
            return tag;
        }

        public static StructureWorldData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    new Factory<>(StructureWorldData::new, StructureWorldData::load, null),
                    DATA_NAME
            );
        }
    }
}