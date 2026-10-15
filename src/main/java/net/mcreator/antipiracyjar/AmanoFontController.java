package net.mcreator.antipiracyjar;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class AmanoFontController {
    private static final ResourceLocation AMANO = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "amano");
    private static final Set<Font> installed = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Field resolverField;
    private static boolean failed;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        install();
    }

    public static boolean install() {
        if (failed) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null || minecraft.fontFilterFishy == null) return false;
        try {
            if (resolverField == null) {
                for (Field field : Font.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == Function.class) {
                        if (resolverField != null) throw new IllegalStateException("Ambiguous Font resolver");
                        resolverField = field;
                    }
                }
                if (resolverField == null) throw new NoSuchFieldException("Font resolver");
                resolverField.setAccessible(true);
            }
            wrap(minecraft.font);
            wrap(minecraft.fontFilterFishy);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failed = true;
            LogUtils.getLogger().error("[Amano] Could not install font resolver", exception);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void wrap(Font font) throws IllegalAccessException {
        if (installed.contains(font)) return;
        Function<ResourceLocation, FontSet> original =
                (Function<ResourceLocation, FontSet>) resolverField.get(font);
        Function<ResourceLocation, FontSet> replacement = id -> {
            Minecraft minecraft = Minecraft.getInstance();
            boolean enabled = FakeKickScreen.amanoEnabled
                    && !(minecraft.screen instanceof TitleScreen)
                    && !(minecraft.screen instanceof FakeKickScreen)
                    && (minecraft.screen != null || minecraft.level != null);
            if (enabled && id.getNamespace().equals("minecraft")
                    && (id.getPath().equals("default") || id.getPath().equals("uniform"))) {
                return original.apply(AMANO);
            }
            return original.apply(id);
        };
        resolverField.set(font, replacement);
        installed.add(font);
        LogUtils.getLogger().info("[Amano] Font resolver installed ({}/2)", installed.size());
    }
}
