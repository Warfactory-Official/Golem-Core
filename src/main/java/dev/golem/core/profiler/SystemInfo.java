package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.OperatingSystemMXBean;
import dev.golem.core.platform.Platform;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/** Machine, JVM and mod list. Paths under the user's home are shortened to {@code ~} so reports can be shared. */
final class SystemInfo {

    private static final Pattern SECRET = Pattern.compile("(?i)token|password|secret|session|uuid|xuid|clientid");

    private SystemInfo() {}

    static JsonObject collect() {
        JsonObject json = new JsonObject();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        json.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        json.addProperty("cpu", cpuName());
        json.addProperty("cores", Runtime.getRuntime().availableProcessors());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
            json.addProperty("memoryMb", os.getTotalMemorySize() / 1048576);
            json.addProperty("freeMemoryMb", os.getFreeMemorySize() / 1048576);
        }
        json.addProperty("java", System.getProperty("java.version") + " " + System.getProperty("java.vendor"));
        json.addProperty("vm", runtime.getVmName() + " " + runtime.getVmVersion());
        json.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() / 1048576);
        json.addProperty("uptimeSeconds", runtime.getUptime() / 1000);
        JsonArray gcs = new JsonArray();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) gcs.add(gc.getName());
        json.add("gc", gcs);
        JsonArray args = new JsonArray();
        for (String arg : runtime.getInputArguments()) {
            if (!SECRET.matcher(arg).find()) args.add(anonymise(arg));
        }
        json.add("jvmArgs", args);
        JsonArray mods = new JsonArray();
        for (Map.Entry<String, String> mod : Platform.loadedMods().entrySet()) {
            JsonArray row = new JsonArray();
            row.add(mod.getKey());
            row.add(mod.getValue());
            mods.add(row);
        }
        json.add("mods", mods);
        return json;
    }

    static String anonymise(String text) {
        String home = System.getProperty("user.home");
        if (home == null || home.length() < 3) return text;
        return text.replace(home, "~").replace(home.replace('\\', '/'), "~");
    }

    private static String cpuName() {
        try {
            Path info = Path.of("/proc/cpuinfo");
            if (Files.isReadable(info)) {
                for (String line : Files.readAllLines(info)) {
                    if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException unreadable) {
            // Not Linux, or a sandbox hides it: fall back to what the environment says.
        }
        String identifier = System.getenv("PROCESSOR_IDENTIFIER");
        return identifier != null ? identifier : System.getProperty("os.arch");
    }
}
