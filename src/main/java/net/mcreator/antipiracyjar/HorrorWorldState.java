package net.mcreator.antipiracyjar;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
public final class HorrorWorldState extends SavedData {
    public CompoundTag data = new CompoundTag();
    public static HorrorWorldState get(MinecraftServer server) {
        HorrorWorldState state = server.overworld().getDataStorage().computeIfAbsent(new Factory<>(HorrorWorldState::new, HorrorWorldState::load, null), "antipiracy_story");
        if (!state.data.getBoolean("migrated")) {
            state.data.putBoolean("migrated", true);
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("antipiracyjar_client_state.properties");
            if (java.nio.file.Files.isRegularFile(file)) {
                try (var in = java.nio.file.Files.newInputStream(file)) {
                    var props = new java.util.Properties(); props.load(in);
                    int stage = Math.max(0, Math.min(6, Integer.parseInt(props.getProperty("stage", "0"))));
                    state.data.putInt("storyStage", stage);
                    state.data.putBoolean("fontEnabled", stage > 0);
                    if (stage >= 6) { state.data.putBoolean("messagesFinished", true); state.data.putLong("messagesFinishedDay", server.overworld().getDayTime() / 24000); }
                } catch (Exception ex) { AntiPiracyjarMod.LOGGER.warn("Could not migrate old story progress", ex); }
            }
            state.setDirty();
        }
        return state;
    }
    private static HorrorWorldState load(CompoundTag tag, HolderLookup.Provider provider) {
        HorrorWorldState state = new HorrorWorldState(); state.data = tag.copy(); return state;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { return data.copy(); }
    public void flush(MinecraftServer server) { setDirty(); server.overworld().getDataStorage().save(); }
}
