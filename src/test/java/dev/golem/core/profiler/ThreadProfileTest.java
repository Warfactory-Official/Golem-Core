package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThreadProfileTest {

    private static StackTraceElement frame(String type, String method) {
        return new StackTraceElement(type, method, null, -1);
    }

    @Test
    void foldsStacksIntoTreeAndCountsRecursionOnce() {
        ThreadProfile profile = new ThreadProfile("Render thread");
        StackTraceElement[] recursive = {
                frame("com.hbm.Render", "part"), frame("com.hbm.Render", "part"), frame("net.minecraft.Main", "run")};
        StackTraceElement[] other = {frame("java.lang.Object", "wait"), frame("net.minecraft.Main", "run")};
        profile.add(recursive, "RUNNABLE", 4000);
        profile.add(other, "WAITING", 4000);

        ThreadProfile.Node main = profile.root.find("net.minecraft.Main.run");
        assertNotNull(main);
        assertEquals(8000, main.total);
        ThreadProfile.Node outer = main.find("com.hbm.Render.part");
        assertEquals(4000, outer.find("com.hbm.Render.part").self);

        JsonObject json = profile.toJson(10);
        JsonArray part = row(json.getAsJsonArray("methods"), "com.hbm.Render.part");
        assertEquals(4.0, part.get(1).getAsDouble());
        assertEquals(4.0, part.get(2).getAsDouble(), "a recursive method counts once per sample");
        assertEquals(8.0, row(json.getAsJsonArray("groups"), "Minecraft").get(2).getAsDouble());
        assertEquals(4.0, json.getAsJsonObject("states").get("WAITING").getAsDouble());
    }

    @Test
    void groupsByModAndMixinHandler() {
        assertEquals("HBM", CodeGroups.of("com.hbm.blocks.Foo", "bar"));
        assertEquals("Minecraft", CodeGroups.of("net.minecraft.client.renderer.LevelRenderer", "renderLevel"));
        assertEquals("mixin from sodium",
                CodeGroups.of("net.minecraft.client.renderer.LevelRenderer", "handler$zzb000$sodium$onRender"));
        assertEquals("org.example", CodeGroups.of("org.example.mod.Thing", "tick"));
        assertEquals("Worker-Main-#", StackSampler.poolName("Worker-Main-12"));
    }

    private static JsonArray row(JsonArray table, String key) {
        for (int i = 0; i < table.size(); i++) {
            JsonArray row = table.get(i).getAsJsonArray();
            if (row.get(0).getAsString().equals(key)) return row;
        }
        throw new AssertionError("no row " + key);
    }
}
