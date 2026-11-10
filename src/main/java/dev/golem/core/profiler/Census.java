package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import java.util.Map;

final class Census {

    private Census() {}

    /** {@code [[id, count], ...]}, most numerous first. */
    static JsonArray sorted(Map<String, Integer> counts) {
        JsonArray rows = new JsonArray();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    JsonArray row = new JsonArray();
                    row.add(entry.getKey());
                    row.add(entry.getValue());
                    rows.add(row);
                });
        return rows;
    }
}
