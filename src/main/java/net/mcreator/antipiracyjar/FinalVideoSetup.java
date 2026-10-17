package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FinalVideoSetup {
    public static final int FPS = 25;

    private static final String DOWNLOAD_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static CompletableFuture<List<Path>> preparation;

    private FinalVideoSetup() {
    }

    public static synchronized CompletableFuture<List<Path>> prepare() {
        if (preparation == null) {
            Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
            preparation = CompletableFuture.supplyAsync(() -> {
                try {
                    return prepareFiles(gameDirectory);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            });
        }
        return preparation;
    }

    private static List<Path> prepareFiles(Path gameDirectory) throws Exception {
        Path base = gameDirectory.resolve("config/antipiracyjar");
        Path videos = base.resolve("videos");
        Path frames = base.resolve("cadri/finalvideo");
        Path settings = base.resolve("config");
        Path executable = gameDirectory.resolve("config/ffmpeglib/ffmpeg.exe");

        Files.createDirectories(videos);
        Files.createDirectories(frames);
        Files.createDirectories(settings);

        Path video = videos.resolve("finalvideo.mp4");
        Path temporaryVideo = videos.resolve("finalvideo.mp4.part");
        try (InputStream input = FinalVideoSetup.class.getResourceAsStream("/assets/anti_piracyjar/videos/finalvideo.mp4")) {
            if (input == null) {
                throw new IOException("Missing resource: assets/anti_piracyjar/videos/finalvideo.mp4");
            }
            Files.copy(input, temporaryVideo, StandardCopyOption.REPLACE_EXISTING);
        }

        if (Files.size(temporaryVideo) == 0) {
            Files.deleteIfExists(temporaryVideo);
            throw new IOException("finalvideo.mp4 is empty");
        }

        String signature = hash(temporaryVideo) + ":" + FPS;
        Files.move(temporaryVideo, video, StandardCopyOption.REPLACE_EXISTING);
        Path manifest = settings.resolve("finalvideo.cache");
        List<Path> existing = listFrames(frames);

        if (Files.isRegularFile(manifest)) {
            List<String> metadata = Files.readAllLines(manifest);
            if (metadata.size() == 2 && metadata.get(0).equals(signature)
                    && metadata.get(1).equals(Integer.toString(existing.size()))
                    && validFrames(existing)) {
                return existing;
            }
        }

        installFFmpeg(executable);
        Files.deleteIfExists(manifest);
        for (Path frame : existing) {
            Files.delete(frame);
        }

        Path log = settings.resolve("finalvideo-ffmpeg.log");
        Process process = new ProcessBuilder(
                executable.toAbsolutePath().toString(),
                "-nostdin", "-y", "-i", video.toAbsolutePath().toString(),
                "-an", "-vf", "fps=" + FPS,
                frames.resolve("frame_%06d.png").toAbsolutePath().toString()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();

        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                throw new IOException("FFmpeg timed out. See " + log);
            }
            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg failed. See " + log);
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        List<Path> extracted = listFrames(frames);
        if (!validFrames(extracted)) {
            throw new IOException("FFmpeg produced no complete frame sequence");
        }
        Files.write(manifest, List.of(signature, Integer.toString(extracted.size())));
        return extracted;
    }

    private static void installFFmpeg(Path executable) throws IOException {
        if (Files.isRegularFile(executable) && Files.size(executable) > 0) {
            return;
        }
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            throw new IOException("This FFmpeg setup requires Windows");
        }

        Files.createDirectories(executable.getParent());
        Path temporary = executable.resolveSibling("ffmpeg.exe.part");
        URLConnection connection = URI.create(DOWNLOAD_URL).toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "AntiPiracyJar");

        try (InputStream input = connection.getInputStream();
             ZipInputStream archive = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith("/bin/ffmpeg.exe")) {
                    Files.copy(archive, temporary, StandardCopyOption.REPLACE_EXISTING);
                    if (Files.size(temporary) == 0) {
                        throw new IOException("Downloaded FFmpeg executable is empty");
                    }
                    Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
                archive.closeEntry();
            }
            throw new IOException("ffmpeg.exe was not found in the downloaded archive");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<Path> listFrames(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("frame_\\d{6}\\.png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean validFrames(List<Path> frames) throws IOException {
        if (frames.isEmpty()) {
            return false;
        }
        for (int index = 0; index < frames.size(); index++) {
            Path frame = frames.get(index);
            String expected = String.format(java.util.Locale.ROOT, "frame_%06d.png", index + 1);
            if (!frame.getFileName().toString().equals(expected) || Files.size(frame) == 0) {
                return false;
            }
        }
        return true;
    }

    private static String hash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
