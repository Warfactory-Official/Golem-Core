package dev.golem.harness.cli;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** {@code report.md} for people and {@code report.json} for agents. */
public final class Report {

    private Report() {}

    public static void write(Path outDir, String scenario, List<Runner.Result> results) throws IOException {
        Files.createDirectories(outDir);
        StringBuilder md = new StringBuilder();
        md.append("# Harness run: ").append(scenario).append("\n\n");
        md.append("Run at ").append(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)).append("\n\n");
        md.append("| Node | Result | Time | Message |\n|---|---|---|---|\n");
        for (Runner.Result r : results) {
            md.append("| ").append(r.node()).append(" | ").append(r.passed() ? "PASS" : "FAIL").append(" | ")
                    .append(r.millis() / 1000.0).append(" s | ").append(r.message().replace("|", "\\|").replace("\n", " ")).append(" |\n");
        }
        for (Runner.Result r : results) {
            md.append("\n## ").append(r.node()).append(" — ").append(r.passed() ? "PASS" : "FAIL").append("\n\n");
            if (!r.screenshots().isEmpty()) {
                md.append("Screenshots:\n\n");
                for (String shot : r.screenshots()) {
                    Path rel = outDir.toAbsolutePath().relativize(Path.of(shot).toAbsolutePath());
                    md.append("- [").append(rel).append("](").append(rel.toString().replace('\\', '/')).append(")\n");
                }
                md.append('\n');
            }
            if (!r.passed() && !r.stack().isBlank()) {
                md.append("```\n").append(r.stack().strip()).append("\n```\n");
            }
        }
        Files.writeString(outDir.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);

        JsonObject json = new JsonObject();
        json.addProperty("scenario", scenario);
        json.addProperty("passed", results.stream().allMatch(Runner.Result::passed));
        JsonArray nodes = new JsonArray();
        for (Runner.Result r : results) {
            JsonObject o = new JsonObject();
            o.addProperty("node", r.node());
            o.addProperty("passed", r.passed());
            o.addProperty("message", r.message());
            o.addProperty("millis", r.millis());
            JsonArray shots = new JsonArray();
            r.screenshots().forEach(shots::add);
            o.add("screenshots", shots);
            JsonArray transcript = new JsonArray();
            r.transcript().forEach(transcript::add);
            o.add("transcript", transcript);
            if (!r.passed()) o.addProperty("stack", r.stack());
            nodes.add(o);
        }
        json.add("nodes", nodes);
        Files.writeString(outDir.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
    }
}
