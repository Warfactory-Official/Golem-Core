package dev.golem.harness.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The JSON written by the capture main embedded in {@code Golem-Core/gradle/harness-spec.gradle.kts}. */
public record LaunchSpec(String javaExecutable, String javaHome, String mainClass, String workingDir,
                         List<String> jvmArgs, List<String> classpath, List<String> programArgs,
                         Map<String, String> env) {

    public static LaunchSpec read(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> env = new LinkedHashMap<>();
        JsonObject e = json.getAsJsonObject("env");
        for (String key : e.keySet()) env.put(key, e.get(key).getAsString());
        return new LaunchSpec(json.get("javaExecutable").getAsString(), json.get("javaHome").getAsString(),
                json.get("mainClass").getAsString(), json.get("workingDir").getAsString(),
                list(json.getAsJsonArray("jvmArgs")), list(json.getAsJsonArray("classpath")),
                list(json.getAsJsonArray("programArgs")), env);
    }

    private static List<String> list(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement el : array) out.add(el.getAsString());
        return out;
    }
}
