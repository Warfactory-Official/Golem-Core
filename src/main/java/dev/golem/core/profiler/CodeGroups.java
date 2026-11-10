package dev.golem.core.profiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which mod or library a stack frame belongs to, judged from its class and method name. */
final class CodeGroups {

    // Mixin names an injected handler "handler$zzb000$<modid>$<method>" inside the target class.
    private static final Pattern MIXIN_HANDLER = Pattern.compile("^[a-zA-Z]+\\$[a-z0-9]+\\$([a-z0-9_]+)\\$");

    private static final String[][] PREFIXES = {
            {"java.", "Java"}, {"javax.", "Java"}, {"jdk.", "Java"}, {"sun.", "Java"}, {"com.sun.", "Java"},
            {"net.minecraft.", "Minecraft"}, {"com.mojang.", "Minecraft"},
            {"org.lwjgl.", "LWJGL (OpenGL, GLFW, OpenAL)"},
            {"net.neoforged.", "NeoForge"}, {"net.minecraftforge.", "Forge"}, {"cpw.mods.", "Forge"},
            {"net.fabricmc.", "Fabric"},
            {"org.spongepowered.", "Mixin"}, {"com.llamalad7.", "Mixin"},
            {"dev.golem.", "Golem Core"}, {"com.hbm.", "HBM"},
            {"dev.engine_room.flywheel.", "Flywheel"},
            {"me.jellysquid.mods.sodium.", "Sodium"}, {"net.caffeinemc.mods.sodium.", "Sodium"},
            {"org.embeddedt.embeddium.", "Embeddium"},
            {"net.irisshaders.", "Iris"}, {"net.coderbot.iris.", "Iris"},
            {"io.netty.", "Netty"}, {"it.unimi.", "fastutil"}, {"com.google.", "Guava/Gson"},
            {"org.joml.", "JOML"}, {"org.apache.", "Apache libraries"}, {"org.slf4j.", "Logging"},
            {"ch.qos.", "Logging"},
    };

    private CodeGroups() {}

    static String of(String className, String methodName) {
        if (methodName.indexOf('$') > 0) {
            Matcher handler = MIXIN_HANDLER.matcher(methodName);
            if (handler.find()) return "mixin from " + handler.group(1);
        }
        for (String[] prefix : PREFIXES) if (className.startsWith(prefix[0])) return prefix[1];
        int first = className.indexOf('.');
        if (first < 0) return className;
        int second = className.indexOf('.', first + 1);
        return second < 0 ? className.substring(0, first) : className.substring(0, second);
    }
}
