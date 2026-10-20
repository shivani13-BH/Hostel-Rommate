package com.hostelmate.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Score of one lifestyle factor for a pair of students. */
public class FactorScore {
    public final String key;
    public final String label;
    public final int weight;          // share of the 100-point total
    public final double similarity;   // 0.0 .. 1.0
    public final String note;         // human readable explanation

    public FactorScore(String key, String label, int weight, double similarity, String note) {
        this.key = key;
        this.label = label;
        this.weight = weight;
        this.similarity = Math.max(0, Math.min(1, similarity));
        this.note = note;
    }

    public double points() {
        return weight * similarity;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("weight", weight);
        m.put("percent", (int) Math.round(similarity * 100));
        m.put("points", Math.round(points() * 10) / 10.0);
        m.put("note", note);
        return m;
    }
}
