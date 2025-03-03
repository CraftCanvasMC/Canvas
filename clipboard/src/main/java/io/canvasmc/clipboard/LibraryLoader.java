package io.canvasmc.clipboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LibraryLoader {
    private static final List<String> REPOSITORIES = List.of(
        "https://repo.papermc.io/repository/maven-public/",
        "https://jitpack.io",
        "https://s01.oss.sonatype.org/content/repositories/snapshots/",
        "https://repo1.maven.org/maven2/",
        "https://libraries.minecraft.net/"
    );

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }

    protected void start(Main.Provider<String> versionProvider) throws IOException {
        try {
            start(versionProvider.get());
        } catch (Throwable e) {
            throw new RuntimeException("Error extracting libraries!", e);
        }
    }

    protected void start(String mcVersion) throws URISyntaxException {
        System.out.println("Unpacking and linking library jars");
        Path librariesDir = Paths.get("libraries");
        Path clipboardJar = Paths.get(LibraryLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.exists(librariesDir)) {
            try {
                Files.createDirectories(librariesDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create libraries directory", e);
            }
        }

        try (JarFile jarFile = new JarFile(clipboardJar.toFile())) {
            extractLibraries(jarFile, librariesDir);
        } catch (Exception e) {
            throw new RuntimeException("Error processing libraries", e);
        }

        Path vanillaBundler = Paths.get("cache/vanilla-bundler-" + mcVersion + ".jar");
        try (JarFile jarFile = new JarFile(vanillaBundler.toFile())) {
            extractLibraries(jarFile, librariesDir);
        } catch (Exception e) {
            throw new RuntimeException("Error processing libraries", e);
        }
    }

    private void extractLibraries(JarFile jarFile, Path librariesDir) throws IOException {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().startsWith("META-INF/libraries/")) {
                File extractedFile = new File(librariesDir.toFile(), entry.getName().substring("META-INF/libraries/".length()));
                if (entry.isDirectory()) {
                    extractedFile.mkdirs();
                } else if (entry.getName().endsWith(".patch")) {
                    processPatchEntry(entry);
                } else {
                    try (InputStream in = jarFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(extractedFile)) {
                        copy(in, out);
                        Instrumentation.INSTRUMENTATION.appendToSystemClassLoaderSearch(new JarFile(extractedFile));
                    }
                }
            }
        }
    }

    private void processPatchEntry(JarEntry entry) throws IOException {
        InputStream inputStream = LibraryLoader.class.getResourceAsStream("/META-INF/libraries.list");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        boolean foundArtifact = false;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;

            String artifact = parts[1];
            String jarPath = parts[2];
            String artifactFileDirectory = jarPath.replaceFirst("^META-INF/libraries/", "").replaceFirst("/[^/]+$", "");
            String entryName = entry.getRealName().replaceFirst("^META-INF/libraries/", "").replaceFirst("/[^/]+$", "");
            if (entryName.equalsIgnoreCase(artifactFileDirectory)) {
                foundArtifact = true;
                File extractedFile = new File("libraries", jarPath);
                if (!extractedFile.exists()) {
                    File jarFile = attemptDownloadJar(jarPath);
                    if (jarFile == null) {
                        throw new RuntimeException("Failed to download missing library: " + artifact);
                    }
                    Instrumentation.INSTRUMENTATION.appendToSystemClassLoaderSearch(new JarFile(extractedFile));
                    continue;
                }
                Instrumentation.INSTRUMENTATION.appendToSystemClassLoaderSearch(new JarFile(extractedFile));
            }
        }
        if (!foundArtifact) {
            System.out.println("Unable to find library " + entry.getRealName().split("/")[entry.getRealName().split("/").length - 1].replace(".patch", ""));
            System.exit(1);
        }
    }

    private File attemptDownloadJar(String artifact) {
        for (String repo : REPOSITORIES) {
            try {
                URL url = new URL(repo + artifact);
                File downloadedJar = new File("libraries", artifact);
                if (!downloadedJar.exists()) {
                    URLConnection connection = url.openConnection();
                    try (InputStream in = connection.getInputStream();
                         OutputStream out = new FileOutputStream(downloadedJar)) {
                        copy(in, out);
                    }
                }
                if (downloadedJar.exists()) {
                    return downloadedJar;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
