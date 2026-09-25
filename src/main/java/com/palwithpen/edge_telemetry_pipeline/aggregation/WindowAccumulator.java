package com.palwithpen.edge_telemetry_pipeline.aggregation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Holds the raw values for a single 1-minute window and computes avg/p95/count on demand.
// Multiple worker threads can be adding values to the SAME window concurrently, so this
// needs to be genuinely thread-safe, not just "usually fine."
public class WindowAccumulator {

    // CopyOnWriteArrayList on purpose: reads here (average/p95, which iterate the whole
    // list) never block writers and vice versa, since every read sees a stable snapshot.
    // The cost is that every single add() copies the underlying array, which is fine at the
    // volume one device/metric sees in a minute, but would need rethinking (a streaming
    // percentile estimator, say) if per-window volume ever got much higher.
    private final List<Double> values = new CopyOnWriteArrayList<>();

    public void add(double value){
        values.add(value);
    }

    public double average(){
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // p95 = the value below which 95% of readings fall. Computed the straightforward way —
    // sort everything, index 95% of the way in — which means holding every raw value in
    // memory and re-sorting on every call. Fine here; wouldn't scale to millions of readings
    // per window without switching to an approximation algorithm (t-digest, HDRHistogram).
    public double p95() {
        List<Double> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) return 0.0;
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }

    public int count() {
        return values.size();
    }
}
