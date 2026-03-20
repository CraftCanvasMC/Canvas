package io.canvasmc.canvas.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.Util;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CpuTopology {

    private static int readInt(String path, int fallback) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return Integer.parseInt(br.readLine().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static @Nullable String readFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String readFullFile(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) builder.append(line).append("\n");
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static @NonNull String readCacheIndex(int cpu, String type) {
        String base = "/sys/devices/system/cpu/cpu" + cpu + "/cache/";
        for (int i = 0; i < 8; i++) {
            String typeFile = base + "index" + i + "/type";
            String idFile = base + "index" + i + "/id";
            String t = readFile(typeFile);
            if (type.equals(t)) {
                String id = readFile(idFile);
                return id != null ? id : String.valueOf(i);
            }
        }
        return "?";
    }

    private static @NonNull String readCacheIndex(int cpu, String type, int level) {
        String base = "/sys/devices/system/cpu/cpu" + cpu + "/cache/";
        for (int i = 0; i < 8; i++) {
            String typeFile = base + "index" + i + "/type";
            String levelFile = base + "index" + i + "/level";
            String idFile = base + "index" + i + "/id";
            String t = readFile(typeFile);
            String l = readFile(levelFile);
            if (type.equals(t) && String.valueOf(level).equals(l)) {
                String id = readFile(idFile);
                return id != null ? id : String.valueOf(i);
            }
        }
        return "?";
    }

    public static boolean isLinux() {
        return Util.getPlatform().equals(Util.OS.LINUX);
    }

    // note: linux only
    public static @NonNull String compileOutput() {
        StringBuilder out = new StringBuilder();
        List<CpuInfo> cpus = new ArrayList<>();
        int cpuCount = Runtime.getRuntime().availableProcessors();

        String model = Arrays.stream(Objects.requireNonNull(readFullFile("/proc/cpuinfo"), "Couldn't read /proc/cpuinfo").split("\n"))
            .filter(line -> line.startsWith("model name"))
            .map(line -> line.replaceAll(".*: ", ""))
            .findFirst().orElse("");
        out.append("CPU Model: ").append(model).append("\n====================================\n");
        for (int cpu = 0; cpu < cpuCount; cpu++) {
            int core = readInt("/sys/devices/system/cpu/cpu" + cpu + "/topology/core_id", cpu);
            int socket = readInt("/sys/devices/system/cpu/cpu" + cpu + "/topology/physical_package_id", 0);
            String l1d = readCacheIndex(cpu, "Data");
            String l1i = readCacheIndex(cpu, "Instruction");
            String l2 = readCacheIndex(cpu, "Unified", 2);
            String l3 = readCacheIndex(cpu, "Unified", 3);
            cpus.add(new CpuInfo(cpu, core, socket, l1d, l1i, l2, l3));
        }

        out.append(String.format("%-5s %-6s %-7s %s%n", "CPU", "CORE", "SOCKET", "L1d:L1i:L2:L3"));

        for (CpuInfo c : cpus) {
            out.append(String.format("%-5d %-6d %-7d %s:%s:%s:%s%n",
                c.cpu(), c.core(), c.socket(), c.l1d(), c.l1i(), c.l2(), c.l3()));
        }

        return out.toString();
    }

    record CpuInfo(int cpu, int core, int socket, String l1d, String l1i, String l2, String l3) {}
}
