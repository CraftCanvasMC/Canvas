package io.canvasmc.clipboard;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.compress.compressors.CompressorException;

public class PatcherBuilder {
    private static String read(int index, String name) {
        try (InputStream inputStream = PatcherBuilder.class.getResourceAsStream("/META-INF/" + name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line = reader.readLine();
            String[] parts = line.split("\\s+");
            return parts[index].trim();

        } catch (Exception e) {
            throw new RuntimeException("Unable to read " + name + " at index " + index, e);
        }
    }

    public static String getFileSha256(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    protected void start(Main.Provider<String> versionProvider) throws IOException {
        start(versionProvider.get());
    }

    protected void start(String mcVersion) {
        File versionsDirectory = Paths.get("versions").toFile();
        if (!versionsDirectory.exists()) {
            versionsDirectory.mkdirs();
        }

        if (mcVersion == null) throw new RuntimeException("Provided Minecraft version was null!");
        System.out.println("Loading Minecraft version: " + mcVersion);

        try {
            String vanillaUrl;
            String sha256Hash;

            sha256Hash = read(0, "download-context");
            vanillaUrl = read(1, "download-context");

            if (vanillaUrl == null)
                throw new FileNotFoundException("Unable to locate vanilla server download url! Corrupted download context?");
            if (sha256Hash == null) throw new NullPointerException("Unable to locate SHA-256 hash! Corrupted?");

            URL url = new URL(vanillaUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            Path vanillaBundler = Paths.get("cache/vanilla-bundler-" + mcVersion + ".jar");
            Path cacheDirectory = vanillaBundler.getParent();
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
            }

            if (!vanillaBundler.toFile().exists() || !getFileSha256(vanillaBundler.toFile()).equals(sha256Hash)) {
                System.out.println("Downloading vanilla jar...");

                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream fileOutputStream = new FileOutputStream("cache/vanilla-bundler-" + mcVersion + ".jar")) {

                    byte[] buf = new byte[1024 * 8]; // 8kb buffer
                    int i;
                    while ((i = in.read(buf, 0, buf.length)) != -1) {
                        fileOutputStream.write(buf, 0, i);
                    }

                } finally {
                    connection.disconnect();
                }
            }

            System.out.println("Applying patches...");
            String patchedJarName = read(2, "versions.list").split("/")[1];
            try (JarFile jarFile = new JarFile(vanillaBundler.toFile())) {
                JarEntry entry = jarFile.getJarEntry("META-INF/versions/" + mcVersion + "/server-" + mcVersion + ".jar");

                if (entry == null) {
                    System.out.println("Entry not found in the JAR file.");
                    return;
                }

                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    Files.copy(inputStream, Paths.get("cache/vanilla-" + mcVersion + ".jar"), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            File patch = extractResource("/META-INF/versions/" + mcVersion + "/server-" + mcVersion + ".jar.patch", "cache");
            File out = Paths.get("versions/" + mcVersion + "/" + patchedJarName).toFile();
            if (!out.getParentFile().exists()) {
                out.getParentFile().mkdirs();
            }

            Patch.patch(
                new FileInputStream("cache/vanilla-" + mcVersion + ".jar").readAllBytes(),
                new FileInputStream(patch).readAllBytes(),
                new FileOutputStream(out)
            );

            if (!out.exists()) {
                throw new RuntimeException("Version file was not found after patching!");
            }

            Instrumentation.INSTRUMENTATION.appendToSystemClassLoaderSearch(new JarFile(out));
        } catch (IOException | CompressorException | InvalidHeaderException e) {
            throw new RuntimeException("Unable to build patched jar!", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to get SHA-256 hash!", e);
        }
    }

    private File extractResource(String resourcePath, String outputDir) throws IOException {
        try (InputStream resourceStream = PatcherBuilder.class.getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Path outputDirectory = Path.of(outputDir);
            if (Files.notExists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
            }

            Path outputPath = outputDirectory.resolve(Path.of(resourcePath).getFileName());
            File outputFile = outputPath.toFile();

            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                resourceStream.transferTo(outputStream);
            }

            return outputFile;
        }
    }
}
