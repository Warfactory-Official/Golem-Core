package dev.golem.core.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Embeds the capture as JSON in the viewer page, so one file opens in any browser, offline, and still parses. */
final class ReportWriter {

    private static final String TEMPLATE = "/golemcore/profiler/report.html";
    private static final String SLOT = "/*GOLEM_PROFILE*/null";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();

    private ReportWriter() {}

    static void write(JsonObject report, Path file) throws IOException {
        String template;
        try (InputStream in = ReportWriter.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) throw new IOException("missing " + TEMPLATE);
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // "</" would end the script element early.
        String json = GSON.toJson(report).replace("</", "<\\/");
        int slot = template.indexOf(SLOT);
        if (slot < 0) throw new IOException(TEMPLATE + " has no data slot");
        String html = template.substring(0, slot) + json + template.substring(slot + SLOT.length());
        Files.writeString(file, html, StandardCharsets.UTF_8);
    }
}
