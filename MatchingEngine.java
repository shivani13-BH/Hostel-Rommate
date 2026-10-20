package com.hostelmate.engine;

import com.hostelmate.database.Preferences;
import com.hostelmate.database.User;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-factor compatibility scoring.
 *
 * Each of the 10 lifestyle factors produces a similarity between 0 and 1.
 * The similarity is multiplied by the factor's weight and the weighted
 * points are added up, giving a final score from 0 to 100.
 *
 *   Factor            Weight   How similarity is computed
 *   Sleep schedule      18     1 - |a-b|/4  (1..5 scale)
 *   Cleanliness         16     1 - |a-b|/4, extra penalty when the gap is 3+
 *   Noise tolerance     12     1 - |a-b|/4
 *   Study habits        12     1 - |a-b|/4
 *   Social level         8     1 - |a-b|/4
 *   Guests              6      1 - |a-b|/4
 *   Budget              10     1 - |a-b|/3000, floored at 0
 *   Diet                 8     compatibility table (same diet = 1.0)
 *   Smoking              5     same habit = 1.0, different = 0.0
 *   Hobbies              5     Jaccard overlap of hobby sets
 *                       ---
 *                       100
 */
public class MatchingEngine {

    public static final int TOP_N = 5;

    private static final Map<String, Double> DIET_TABLE = new HashMap<>();
    static {
        diet("Vegetarian", "Eggetarian", 0.75);
        diet("Vegetarian", "Vegan", 0.8);
        diet("Vegetarian", "Jain", 0.8);
        diet("Vegetarian", "Non-vegetarian", 0.35);
        diet("Eggetarian", "Non-vegetarian", 0.65);
        diet("Eggetarian", "Vegan", 0.5);
        diet("Eggetarian", "Jain", 0.4);
        diet("Vegan", "Jain", 0.7);
        diet("Vegan", "Non-vegetarian", 0.2);
        diet("Jain", "Non-vegetarian", 0.15);
    }

    private static void diet(String a, String b, double v) {
        DIET_TABLE.put(a + "|" + b, v);
        DIET_TABLE.put(b + "|" + a, v);
    }

    /** Scores two preference profiles. The result is symmetric: score(a,b) == score(b,a). */
    public MatchResult score(User candidate, Preferences a, Preferences b) {
        List<FactorScore> f = new ArrayList<>();

        f.add(scale("sleep", "Sleep schedule", 18, a.sleepSchedule, b.sleepSchedule,
                "similar bedtimes", "one of you is an early bird, the other a night owl"));

        double clean = scaleSim(a.cleanliness, b.cleanliness);
        if (Math.abs(a.cleanliness - b.cleanliness) >= 3) clean *= 0.5; // biggest source of fights
        f.add(new FactorScore("cleanliness", "Cleanliness", 16, clean,
                note(clean, "same standard of tidiness", "very different ideas of a clean room")));

        f.add(scale("noise", "Noise tolerance", 12, a.noiseTolerance, b.noiseTolerance,
                "comfortable with the same noise level", "one needs quiet, the other doesn't mind noise"));
        f.add(scale("study", "Study habits", 12, a.studyHabit, b.studyHabit,
                "study in a similar way", "different study styles in the room"));
        f.add(scale("social", "Social level", 8, a.socialLevel, b.socialLevel,
                "similar social energy", "one is far more social than the other"));
        f.add(scale("guests", "Guests", 6, a.guestFrequency, b.guestFrequency,
                "same comfort with visitors", "different expectations about visitors"));

        int gap = Math.abs(a.budget - b.budget);
        double budget = 1.0 - Math.min(1.0, gap / 3000.0);
        f.add(new FactorScore("budget", "Budget", 10, budget,
                gap == 0 ? "identical budget" : "budgets differ by ₹" + gap + " per month"));

        double diet = a.diet.equals(b.diet) ? 1.0 : DIET_TABLE.getOrDefault(a.diet + "|" + b.diet, 0.5);
        f.add(new FactorScore("diet", "Diet", 8, diet,
                a.diet.equals(b.diet) ? "both " + a.diet.toLowerCase() : a.diet + " and " + b.diet));

        double smoke = a.smoker == b.smoker ? 1.0 : 0.0;
        f.add(new FactorScore("smoking", "Smoking", 5, smoke,
                smoke == 1.0 ? (a.smoker ? "both smoke" : "both non-smokers") : "only one of you smokes"));

        double hobbies = jaccard(a.hobbies, b.hobbies);
        Set<String> common = new TreeSet<>(a.hobbies);
        common.retainAll(b.hobbies);
        f.add(new FactorScore("hobbies", "Hobbies", 5, hobbies,
                common.isEmpty() ? "no shared hobbies yet" : "shared: " + String.join(", ", common)));

        double total = 0;
        for (FactorScore s : f) total += s.points();
        return new MatchResult(candidate, (int) Math.round(total), f);
    }

    /**
     * Ranks every eligible student for {@code me} and returns the best {@code limit}.
     * Eligible = another student, same gender (hostel wings are separate), profile completed,
     * and not already in a confirmed or pending booking with someone else.
     */
    public List<MatchResult> topMatches(User me, Collection<User> everyone, Set<Integer> unavailable, int limit) {
        if (me.preferences == null) return List.of();
        return everyone.stream()
                .filter(u -> u.id != me.id)
                .filter(u -> u.role == User.Role.STUDENT)
                .filter(User::hasProfile)
                .filter(u -> u.gender != null && u.gender.equals(me.gender))
                .filter(u -> !unavailable.contains(u.id))
                .map(u -> score(u, me.preferences, u.preferences))
                .sorted(Comparator.comparingInt((MatchResult r) -> r.score).reversed()
                        .thenComparing(r -> r.candidate.name))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ helpers

    static double scaleSim(int a, int b) {
        return 1.0 - Math.abs(a - b) / 4.0;
    }

    private FactorScore scale(String key, String label, int weight, int a, int b, String good, String bad) {
        double s = scaleSim(a, b);
        return new FactorScore(key, label, weight, s, note(s, good, bad));
    }

    private static String note(double s, String good, String bad) {
        if (s >= 0.75) return good;
        if (s >= 0.5) return "a small difference you can talk through";
        return bad;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.5; // no information either way
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / union.size();
    }
}
