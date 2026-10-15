package net.mcreator.antipiracyjar;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class VhsBackgroundSound {
    private static final float VHS_VOLUME = 0.18F;
    private static final ResourceLocation SOUND_ID =
            ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "vhs");

    private static volatile float volume;
    private static volatile boolean shuttingDown;
    private static volatile SourceDataLine activeLine;
    private static Thread audioThread;
    private static int retryTicks;
    private static boolean shutdownHookInstalled;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        volume = Math.max(0.0F, Math.min(1.0F,
                VHS_VOLUME * minecraft.options.getSoundSourceVolume(SoundSource.MASTER)));

        if (shuttingDown || (audioThread != null && audioThread.isAlive())) {
            return;
        }
        if (retryTicks > 0) {
            retryTicks--;
            return;
        }
        retryTicks = 200;

        WeighedSoundEvents soundEvent = minecraft.getSoundManager().getSoundEvent(SOUND_ID);
        if (soundEvent == null) {
            return;
        }

        ResourceLocation soundFile = soundEvent.getSound(RandomSource.create()).getPath();
        Resource resource = minecraft.getResourceManager().getResource(soundFile).orElse(null);
        if (resource == null) {
            return;
        }

        if (!shutdownHookInstalled) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shuttingDown = true;
                SourceDataLine line = activeLine;
                if (line != null) {
                    line.close();
                }
            }, "AntiPiracy-VHS-Shutdown"));
            shutdownHookInstalled = true;
        }

        audioThread = new Thread(() -> playLoop(resource), "AntiPiracy-VHS-Audio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    private static void playLoop(Resource resource) {
        SourceDataLine line = null;
        try {
            AudioFormat format;
            byte[] pcm;
            try (InputStream input = resource.open();
                 JOrbisAudioStream decoder = new JOrbisAudioStream(input)) {
                format = decoder.getFormat();
                ByteBuffer decoded = decoder.readAll();
                pcm = new byte[decoded.remaining()];
                decoded.get(pcm);
            }

            if (pcm.length == 0 || format.getSampleSizeInBits() != 16 || format.isBigEndian()) {
                throw new IOException("VHS requires non-empty signed 16-bit little-endian PCM.");
            }

            int frameSize = format.getFrameSize();
            int usableLength = pcm.length - pcm.length % frameSize;
            if (usableLength == 0) {
                throw new IOException("VHS audio contains no complete frames.");
            }
            int chunkSize = Math.max(frameSize, 4096 / frameSize * frameSize);
            byte[] chunk = new byte[chunkSize];

            line = AudioSystem.getSourceDataLine(format);
            activeLine = line;
            line.open(format, chunkSize * 4);
            line.start();

            int position = 0;
            while (!shuttingDown && line.isOpen()) {
                float gain = volume;
                for (int i = 0; i < chunk.length; i += 2) {
                    int sample = (short) ((pcm[position] & 255) | (pcm[position + 1] << 8));
                    int scaled = Math.round(sample * gain);
                    chunk[i] = (byte) scaled;
                    chunk[i + 1] = (byte) (scaled >> 8);
                    position += 2;
                    if (position >= usableLength) {
                        position = 0;
                    }
                }

                int written = 0;
                while (written < chunk.length && !shuttingDown && line.isOpen()) {
                    int count = line.write(chunk, written, chunk.length - written);
                    if (count <= 0) {
                        throw new IOException("VHS audio output stopped accepting samples.");
                    }
                    written += count;
                }
            }
        } catch (Exception exception) {
            if (!shuttingDown) {
                LogUtils.getLogger().warn("Cannot play anti_piracyjar:vhs; retrying in 10 seconds", exception);
            }
        } finally {
            if (line != null) {
                line.close();
            }
            activeLine = null;
        }
    }
}
