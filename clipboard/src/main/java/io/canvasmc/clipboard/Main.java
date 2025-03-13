package io.canvasmc.clipboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {

    public static void main(String[] arguments) {
        (new Main()).run(arguments);
    }

    private void run(String[] arguments) {
        try {
            String defaultMainClassName = this.readMainClass(BufferedReader::readLine);
            String mainClassName = System.getProperty("bundlerMainClass", defaultMainClassName);
            String repoDir = System.getProperty("bundlerRepoDir", "");
            Path outputDir = Paths.get(repoDir);
            Files.createDirectories(outputDir);
            Provider<String> versionProvider = () -> {
                InputStream inputStream = Main.class.getResourceAsStream("/version.json");

                if (inputStream == null) {
                    throw new IOException("Unable to locate version resource!");
                }

                String jsonContent;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    jsonContent = sb.toString();
                }

                return jsonContent.split("\"id\": \"")[1].split("\"")[0];
            };
            new PatcherBuilder().start(versionProvider);
            if (Boolean.getBoolean("paperclip.patchonly")) {
                System.exit(0);
            }
            new LibraryLoader().start(versionProvider);
            if (mainClassName == null || mainClassName.isEmpty()) {
                System.out.println("Empty main class specified, exiting");
                System.exit(0);
            }

            System.out.println("Starting " + mainClassName);
            Thread runThread = new Thread(() -> {
                try {
                    Class<?> mainClass = Class.forName(mainClassName);
                    MethodHandle mainHandle = MethodHandles.lookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).asFixedArity();
                    mainHandle.invoke((Object) arguments);
                } catch (Throwable var5x) {
                    Main.Thrower.INSTANCE.sneakyThrow(var5x);
                }
            }, "main");
            runThread.start();
        } catch (Exception var10) {
            var10.printStackTrace(System.out);
            System.out.println("Failed to extract server libraries, exiting");
        }
    }

    private <T> T readMainClass(ResourceParser<T> parser) throws Exception {
        String fullPath = "/META-INF/main-class";

        T var5;
        try (InputStream is = this.getClass().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Resource " + fullPath + " not found");
            }

            var5 = parser.parse(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
        }

        return var5;
    }

    @FunctionalInterface
    private interface ResourceParser<T> {
        T parse(BufferedReader var1) throws Exception;
    }

    public interface Provider<T> {
        T get() throws IOException;
    }

    private static class Thrower<T extends Throwable> {
        private static final Main.Thrower<RuntimeException> INSTANCE = new Main.Thrower<>();

        public void sneakyThrow(Throwable exception) throws T {
            throw new RuntimeException(exception);
        }
    }
}
