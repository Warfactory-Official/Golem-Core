package dev.golem.core.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Embeds the capture as JSON in the viewer page, so one file opens in any browser, offline, and still parses. The JSON
 * is streamed into the file: a busy modpack's capture is too large to build as one string.
 */
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
        int slot = template.indexOf(SLOT);
        if (slot < 0) throw new IOException(TEMPLATE + " has no data slot");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(template, 0, slot);
            GSON.toJson(report, new ScriptSafe(out));
            out.write(template, slot + SLOT.length(), template.length() - slot - SLOT.length());
        }
    }

    /** The bare capture, for when the page cannot be written. */
    static void writeJson(JsonObject report, Path file) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(report, out);
        }
    }

    /** Writes "</" as "<\/", which would otherwise end the script element early. */
    private static final class ScriptSafe extends FilterWriter {
        private boolean afterLess;

        ScriptSafe(Writer out) {
            super(out);
        }

        @Override
        public void write(int c) throws IOException {
            if (afterLess && c == '/') out.write('\\');
            afterLess = c == '<';
            out.write(c);
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            for (int i = 0; i < length; i++) write(buffer[offset + i]);
        }

        @Override
        public void write(String text, int offset, int length) throws IOException {
            for (int i = 0; i < length; i++) write(text.charAt(offset + i));
        }

        // The page's own stream closes it; Gson must not.
        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
