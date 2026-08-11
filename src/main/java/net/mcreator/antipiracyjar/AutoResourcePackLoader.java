package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AutoResourcePackLoader {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                Path packsDir = mc.getResourcePackDirectory();
                Path targetPath = packsDir.resolve("theoriginalworkalpha.zip");

                try (InputStream in = AutoResourcePackLoader.class.getResourceAsStream("/assets/anti_piracyjar/resourcepacks/theoriginalworkalpha.zip")) {
                    if (in != null) {
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                PackRepository repository = mc.getResourcePackRepository();
                repository.reload();

                String packId = "file/theoriginalworkalpha.zip";
                List<String> selectedPacks = new ArrayList<>(repository.getSelectedIds());

                if (!selectedPacks.contains(packId)) {
                    selectedPacks.add(packId);
                    repository.setSelected(selectedPacks);
                    mc.options.save();
                    mc.reloadResourcePacks();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}