package com.palwithpen.edge_telemetry_pipeline.aggregation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WindowAccumulator {

    private final List<Double> values = new CopyOnWriteArrayList<>();

    public void add(double value){
        values.add(value);
    }
    
    public double average(){
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

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
