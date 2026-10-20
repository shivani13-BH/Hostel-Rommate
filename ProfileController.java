package com.hostelmate.api;

import com.hostelmate.engine.MatchResult;
import com.hostelmate.database.Preferences;
import com.hostelmate.database.User;

import java.util.*;

public class ProfileController {
    private final AppContext ctx;

    public ProfileController(AppContext ctx) {
        this.ctx = ctx;
    }

    public void register(Router r) {
        r.add("GET", "/api/options", Router.Access.PUBLIC, (req, v) -> Map.of(
                "diets", List.of(Preferences.DIETS),
                "hobbies", List.of(Preferences.HOBBY_OPTIONS)));
        r.add("POST", "/api/profile", Router.Access.STUDENT, this::saveProfile);
        r.add("GET", "/api/matches", Router.Access.STUDENT, this::matches);
        r.add("GET", "/api/users/{id}", Router.Access.STUDENT, this::viewUser);
    }

    private Object saveProfile(Request req, Map<String, String> v) {
        Preferences p = new Preferences();
        p.sleepSchedule = req.integer("sleepSchedule", "Sleep schedule", 1, 5);
        p.cleanliness = req.integer("cleanliness", "Cleanliness", 1, 5);
        p.noiseTolerance = req.integer("noiseTolerance", "Noise tolerance", 1, 5);
        p.studyHabit = req.integer("studyHabit", "Study habits", 1, 5);
        p.socialLevel = req.integer("socialLevel", "Social level", 1, 5);
        p.guestFrequency = req.integer("guestFrequency", "Guests", 1, 5);
        p.budget = req.integer("budget", "Monthly budget", 1000, 50000);
        p.diet = req.required("diet", "Diet", 20);
        if (!Arrays.asList(Preferences.DIETS).contains(p.diet)) throw new ApiException(400, "Choose a diet from the list.");
        p.smoker = "true".equals(req.str("smoker"));
        List<String> allowed = Arrays.asList(Preferences.HOBBY_OPTIONS);
        for (String h : req.str("hobbies").split(",")) {
            String t = h.trim();
            if (!t.isEmpty() && allowed.contains(t)) p.hobbies.add(t);
        }
        String bio = req.str("bio");
        if (bio.length() > 300) throw new ApiException(400, "About me must be at most 300 characters.");
        p.bio = bio;

        User me = req.user;
        me.preferences = p;
        ctx.store.updateUser(me);
        return me.toPrivateMap();
    }

    private Object matches(Request req, Map<String, String> v) {
        User me = req.user;
        if (!me.hasProfile()) throw new ApiException(409, "Complete your lifestyle profile to see matches.");
        List<MatchResult> top = ctx.engine.topMatches(me, ctx.store.allUsers(), ctx.alreadyHoused(), com.hostelmate.engine.MatchingEngine.TOP_N);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchResult m : top) out.add(m.toMap());
        return Map.of("matches", out);
    }

    private Object viewUser(Request req, Map<String, String> v) {
        User other = ctx.requireUser(Integer.parseInt(v.get("id")), "Student");
        if (other.role != User.Role.STUDENT) throw new ApiException(404, "Student was not found.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", other.toPublicMap());
        if (req.user.hasProfile() && other.hasProfile() && other.id != req.user.id)
            m.put("match", ctx.engine.score(other, req.user.preferences, other.preferences).toMap());
        return m;
    }
}
