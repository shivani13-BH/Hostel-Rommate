package com.hostelmate.engine;

import com.hostelmate.database.Preferences;
import com.hostelmate.database.User;

import java.util.*;

/**
 * Self-contained tests for the matching engine (no JUnit needed).
 * Run: java -cp out com.hostelmate.MatchingEngineTest
 */
public class MatchingEngineTest {
    private static int passed = 0, failed = 0;
    private static final MatchingEngine engine = new MatchingEngine();

    public static void main(String[] args) {
        identicalProfilesScore100();
        oppositeProfilesScoreLow();
        scoreIsSymmetric();
        scoreAlwaysBetween0And100();
        weightsAddUpTo100();
        cleanlinessGapIsPenalised();
        topMatchesFiltersAndRanks();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static Preferences prefs(int sleep, int clean, int noise, int study, int social, int guests,
                             int budget, String diet, boolean smoker, String... hobbies) {
        Preferences p = new Preferences();
        p.sleepSchedule = sleep; p.cleanliness = clean; p.noiseTolerance = noise; p.studyHabit = study;
        p.socialLevel = social; p.guestFrequency = guests; p.budget = budget; p.diet = diet; p.smoker = smoker;
        p.hobbies.addAll(List.of(hobbies));
        return p;
    }

    static void identicalProfilesScore100() {
        Preferences a = prefs(2, 4, 2, 2, 3, 2, 6000, "Vegetarian", false, "Music", "Reading");
        check("identical profiles score 100", engine.score(null, a, a).score == 100);
    }

    static void oppositeProfilesScoreLow() {
        Preferences a = prefs(1, 5, 1, 1, 1, 1, 4000, "Jain", false, "Reading");
        Preferences b = prefs(5, 1, 5, 5, 5, 5, 9000, "Non-vegetarian", true, "Gaming");
        int s = engine.score(null, a, b).score;
        check("opposite profiles score below 15 (got " + s + ")", s < 15);
    }

    static void scoreIsSymmetric() {
        Random rnd = new Random(7);
        boolean ok = true;
        for (int i = 0; i < 500; i++) {
            Preferences a = random(rnd), b = random(rnd);
            if (engine.score(null, a, b).score != engine.score(null, b, a).score) ok = false;
        }
        check("score(a,b) == score(b,a) for 500 random pairs", ok);
    }

    static void scoreAlwaysBetween0And100() {
        Random rnd = new Random(11);
        boolean ok = true;
        for (int i = 0; i < 2000; i++) {
            int s = engine.score(null, random(rnd), random(rnd)).score;
            if (s < 0 || s > 100) ok = false;
        }
        check("score stays within 0..100 for 2000 random pairs", ok);
    }

    static void weightsAddUpTo100() {
        Preferences a = prefs(3, 3, 3, 3, 3, 3, 6000, "Vegetarian", false);
        MatchResult r = engine.score(null, a, a);
        int sum = r.factors.stream().mapToInt(f -> f.weight).sum();
        check("factor weights add up to 100 across " + r.factors.size() + " factors", sum == 100 && r.factors.size() >= 8);
    }

    static void cleanlinessGapIsPenalised() {
        Preferences base = prefs(3, 5, 3, 3, 3, 3, 6000, "Vegetarian", false, "Music");
        Preferences smallGap = prefs(3, 4, 3, 3, 3, 3, 6000, "Vegetarian", false, "Music");
        Preferences bigGap = prefs(3, 1, 3, 3, 3, 3, 6000, "Vegetarian", false, "Music");
        int s1 = engine.score(null, base, smallGap).score, s2 = engine.score(null, base, bigGap).score;
        check("large cleanliness gap costs more than a small one (" + s1 + " > " + s2 + ")", s1 > s2 + 10);
    }

    static void topMatchesFiltersAndRanks() {
        User me = user(1, "Female", prefs(2, 4, 2, 2, 3, 2, 6000, "Vegetarian", false, "Music"));
        List<User> all = new ArrayList<>(List.of(me));
        all.add(user(2, "Male", me.preferences));                      // other wing -> excluded
        all.add(user(3, "Female", null));                             // no profile -> excluded
        all.add(user(4, "Female", me.preferences));                   // perfect, but already housed
        Random rnd = new Random(3);
        for (int i = 5; i < 20; i++) all.add(user(i, "Female", random(rnd)));

        List<MatchResult> top = engine.topMatches(me, all, Set.of(4), 5);
        boolean sorted = true;
        for (int i = 1; i < top.size(); i++) if (top.get(i - 1).score < top.get(i).score) sorted = false;
        boolean filtered = top.stream().noneMatch(r -> r.candidate.id == 1 || r.candidate.id == 2
                || r.candidate.id == 3 || r.candidate.id == 4);
        check("top matches: 5 results, sorted, other wing/no-profile/housed excluded",
                top.size() == 5 && sorted && filtered);
    }

    static User user(int id, String gender, Preferences p) {
        User u = new User();
        u.id = id; u.name = "Student " + id; u.gender = gender; u.preferences = p;
        return u;
    }

    static Preferences random(Random rnd) {
        String[] diets = Preferences.DIETS;
        List<String> h = new ArrayList<>(List.of(Preferences.HOBBY_OPTIONS));
        Collections.shuffle(h, rnd);
        return prefs(1 + rnd.nextInt(5), 1 + rnd.nextInt(5), 1 + rnd.nextInt(5), 1 + rnd.nextInt(5),
                1 + rnd.nextInt(5), 1 + rnd.nextInt(5), 3000 + 500 * rnd.nextInt(15),
                diets[rnd.nextInt(diets.length)], rnd.nextBoolean(), h.subList(0, rnd.nextInt(4)).toArray(new String[0]));
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) passed++; else failed++;
    }
}
