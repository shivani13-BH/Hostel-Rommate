package com.hostelmate.database;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifestyle preference profile of a student.
 * Scales are 1..5 so that every factor can be compared on the same footing.
 */
public class Preferences implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String[] DIETS = {"Vegetarian", "Eggetarian", "Non-vegetarian", "Vegan", "Jain"};
    public static final String[] HOBBY_OPTIONS = {
        "Music", "Reading", "Gaming", "Sports", "Gym", "Movies", "Cooking", "Art",
        "Dance", "Coding", "Trekking", "Photography", "Yoga", "Travel"
    };

    /** 1 = early bird (sleeps ~10pm), 5 = night owl (sleeps after 2am). */
    public int sleepSchedule = 3;
    /** 1 = relaxed about mess, 5 = spotless. */
    public int cleanliness = 3;
    /** 1 = needs silence, 5 = fine with noise. */
    public int noiseTolerance = 3;
    /** 1 = studies alone in silence, 5 = group study / music on. */
    public int studyHabit = 3;
    /** 1 = very private, 5 = very social. */
    public int socialLevel = 3;
    /** 1 = never has guests, 5 = guests very often. */
    public int guestFrequency = 3;
    /** Monthly budget for rent in rupees. */
    public int budget = 6000;
    public String diet = "Vegetarian";
    public boolean smoker = false;
    public Set<String> hobbies = new LinkedHashSet<>();
    public String bio = "";

    public static int clampScale(int v) {
        return Math.max(1, Math.min(5, v));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sleepSchedule", sleepSchedule);
        m.put("cleanliness", cleanliness);
        m.put("noiseTolerance", noiseTolerance);
        m.put("studyHabit", studyHabit);
        m.put("socialLevel", socialLevel);
        m.put("guestFrequency", guestFrequency);
        m.put("budget", budget);
        m.put("diet", diet);
        m.put("smoker", smoker);
        m.put("hobbies", hobbies);
        m.put("bio", bio);
        return m;
    }
}
