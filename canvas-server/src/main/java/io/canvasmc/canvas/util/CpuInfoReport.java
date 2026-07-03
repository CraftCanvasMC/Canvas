package io.canvasmc.canvas.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class CpuInfoReport {

    /**
     * Gets if the current OS is Linux based
     *
     * @return if the platform is Linux
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isLinux() {
        return Util.getPlatform().equals(Util.OS.LINUX);
    }

    /**
     * Gathers the CPU topology report similar to the command {@code lscpu -e=CPU,CORE,SOCKET,CACHE}
     *
     * @return the compiled string output of the CPU topology report
     */
    public static String gatherCPUReport() {
        if (!isLinux()) {
            throw new UnsupportedOperationException("not supported in non-linux environments");
        }

        final StringBuilder out = new StringBuilder();
        final List<CpuInfo> cpus = new ArrayList<>();
        final int cpuCount = Runtime.getRuntime().availableProcessors();

        final String model = Arrays.stream(Objects.requireNonNull(readFullFile("/proc/cpuinfo"), "Couldn't read /proc/cpuinfo").split("\n"))
            .filter(line -> line.startsWith("model name"))
            .map(line -> line.replaceAll(".*: ", ""))
            .findFirst().orElse("");

        out.append("CPU Model: ").append(model).append("\n====================================\n");

        for (int cpu = 0; cpu < cpuCount; cpu++) {
            final int core = readInt("/sys/devices/system/cpu/cpu" + cpu + "/topology/core_id", cpu);
            final int socket = readInt("/sys/devices/system/cpu/cpu" + cpu + "/topology/physical_package_id", 0);
            final String l1d = readCacheIndex(cpu, "Data");
            final String l1i = readCacheIndex(cpu, "Instruction");
            final String l2 = readCacheIndex(cpu, "Unified", 2);
            final String l3 = readCacheIndex(cpu, "Unified", 3);
            cpus.add(new CpuInfo(cpu, core, socket, l1d, l1i, l2, l3));
        }

        out.append(String.format("%-5s %-6s %-7s %s%n", "CPU", "CORE", "SOCKET", "L1d:L1i:L2:L3"));

        for (final CpuInfo c : cpus) {
            out.append(String.format("%-5d %-6d %-7d %s:%s:%s:%s%n",
                c.cpu(), c.core(), c.socket(), c.l1d(), c.l1i(), c.l2(), c.l3()));
        }

        return out.toString();
    }

    private static int readInt(final String path, final int fallback) {
        try (final BufferedReader buffered = new BufferedReader(new FileReader(path))) {
            return Integer.parseInt(buffered.readLine().trim());
        } catch (final Throwable ignored) {
            return fallback;
        }
    }

    @Nullable
    private static String readFile(final String path) {
        try (final BufferedReader buffered = new BufferedReader(new FileReader(path))) {
            return buffered.readLine().trim();
        } catch (final Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String readFullFile(final String path) {
        try (final BufferedReader buffered = new BufferedReader(new FileReader(path))) {
            final StringBuilder out = new StringBuilder();
            String line;
            while ((line = buffered.readLine()) != null) {
                out.append(line).append("\n");
            }
            return out.toString();
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static String readCacheIndex(final int cpu, final String type) {
        final String base = "/sys/devices/system/cpu/cpu" + cpu + "/cache/";
        for (int i = 0; i < 8; i++) {
            final String typeFile = base + "index" + i + "/type";
            final String idFile = base + "index" + i + "/id";
            final String t = readFile(typeFile);
            if (type.equals(t)) {
                final String id = readFile(idFile);
                return id != null ? id : String.valueOf(i);
            }
        }
        return "?";
    }

    private static String readCacheIndex(final int cpu, final String type, final int level) {
        final String base = "/sys/devices/system/cpu/cpu" + cpu + "/cache/";
        for (int i = 0; i < 8; i++) {
            final String typeFile = base + "index" + i + "/type";
            final String levelFile = base + "index" + i + "/level";
            final String idFile = base + "index" + i + "/id";
            final String t = readFile(typeFile);
            final String l = readFile(levelFile);
            if (type.equals(t) && String.valueOf(level).equals(l)) {
                final String id = readFile(idFile);
                return id != null ? id : String.valueOf(i);
            }
        }
        return "?";
    }

    record CpuInfo(int cpu, int core, int socket, String l1d, String l1i, String l2, String l3) {}
}
