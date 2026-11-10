package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Timestamped millisecond values (frames, ticks), kept as primitive arrays and summarised with percentiles. */
final class TimeSeries {

    private float[] times = new float[1024];
    private float[] values = new float[1024];
    private int size;

    synchronized void add(double seconds, double millis) {
        if (size == times.length) {
            times = Arrays.copyOf(times, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        times[size] = (float) seconds;
        values[size] = (float) millis;
        size++;
    }

    synchronized int size() {
        return size;
    }

    synchronized double percentile(double fraction) {
        if (size == 0) return 0;
        float[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return percentile(sorted, fraction);
    }

    /** {@code [time - value, time]} in seconds for every value of at least {@code thresholdMs}, oldest first. */
    synchronized List<double[]> windowsOver(double thresholdMs) {
        List<double[]> windows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (values[i] >= thresholdMs) windows.add(new double[]{times[i] - values[i] / 1000.0, times[i]});
        }
        return windows;
    }

    /** {@code {count, mean, p50.., max, t: [...], v: [...]}}; percentiles are of the values, worst last. */
    synchronized JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("count", size);
        if (size == 0) return json;
        float[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        double sum = 0;
        for (int i = 0; i < size; i++) sum += values[i];
        json.addProperty("mean", sum / size);
        json.addProperty("p50", percentile(sorted, 0.50));
        json.addProperty("p90", percentile(sorted, 0.90));
        json.addProperty("p95", percentile(sorted, 0.95));
        json.addProperty("p99", percentile(sorted, 0.99));
        json.addProperty("p999", percentile(sorted, 0.999));
        json.addProperty("max", sorted[size - 1]);
        json.addProperty("sum", sum);
        json.add("t", array(times));
        json.add("v", array(values));
        return json;
    }

    static double percentile(float[] sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private JsonArray array(float[] source) {
        JsonArray array = new JsonArray();
        // Three decimals keep a 30 s capture at a few hundred kilobytes.
        for (int i = 0; i < size; i++) array.add(Math.round(source[i] * 1000) / 1000.0);
        return array;
    }
}
