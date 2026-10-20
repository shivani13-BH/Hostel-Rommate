package com.hostelmate.engine;

import com.hostelmate.database.User;

import java.util.*;

public class MatchResult {
    public final User candidate;
    public final int score;                 // 0..100
    public final List<FactorScore> factors;

    public MatchResult(User candidate, int score, List<FactorScore> factors) {
        this.candidate = candidate;
        this.score = score;
        this.factors = factors;
    }

    /** Factors sorted from best to worst agreement. */
    public List<FactorScore> ranked() {
        List<FactorScore> list = new ArrayList<>(factors);
        list.sort(Comparator.comparingDouble((FactorScore f) -> f.similarity).reversed()
                .thenComparing(f -> -f.weight));
        return list;
    }

    public String label() {
        if (score >= 85) return "Excellent match";
        if (score >= 70) return "Good match";
        if (score >= 55) return "Fair match";
        return "Low match";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", candidate == null ? null : candidate.toPublicMap());
        m.put("score", score);
        m.put("label", label());
        List<Map<String, Object>> fs = new ArrayList<>();
        for (FactorScore f : factors) fs.add(f.toMap());
        m.put("factors", fs);
        List<FactorScore> r = ranked();
        m.put("strengths", List.of(r.get(0).label, r.get(1).label));
        // "Watch out" = the factor that cost the most points, if it cost a meaningful amount.
        FactorScore costliest = factors.stream()
                .max(Comparator.comparingDouble(f -> f.weight - f.points())).orElse(null);
        m.put("watchOut", costliest != null && costliest.similarity < 0.75 && costliest.weight - costliest.points() >= 3
                ? costliest.label + ": " + costliest.note : null);
        return m;
    }
}
