package dev.golem.core.profiler;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @Test
    void streamsDeepTreesIntoThePageAndKeepsTheScriptClosed(@TempDir Path dir) throws Exception {
        StackTraceElement[] stack = new StackTraceElement[384];
        for (int i = 0; i < stack.length; i++) stack[i] = new StackTraceElement("com.example.Deep" + i, "call", null, -1);
        stack[0] = new StackTraceElement("com.example.Html</script>", "leaf", null, -1);
        ThreadProfile profile = new ThreadProfile("Render thread");
        profile.add(stack, "RUNNABLE", 4000);
        JsonObject report = new JsonObject();
        report.add("thread", profile.toJson(10));

        Path page = dir.resolve("profile.html");
        ReportWriter.write(report, page);
        String html = Files.readString(page, StandardCharsets.UTF_8);

        assertTrue(html.contains("com.example.Deep383.call"));
        assertTrue(html.contains("Html<\\/script>"));
        assertFalse(html.contains("/*GOLEM_PROFILE*/"));
        assertTrue(html.trim().endsWith("</html>"));
    }
}
