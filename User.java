package com.hostelmate.database;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Role { STUDENT, ADMIN }

    public int id;
    public String name;
    public String email;
    public String passwordHash;
    public String salt;
    public String gender;      // "Female" or "Male" – hostels are gender-separated
    public String course;
    public int year;
    public Role role = Role.STUDENT;
    public Preferences preferences; // null until the profile is completed
    public long createdAt = System.currentTimeMillis();

    public boolean hasProfile() {
        return preferences != null;
    }

    /** Safe public view: never exposes email, hash or salt. */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("gender", gender);
        m.put("course", course);
        m.put("year", year);
        m.put("preferences", preferences == null ? null : preferences.toMap());
        return m;
    }

    /** View for the logged-in user themselves. */
    public Map<String, Object> toPrivateMap() {
        Map<String, Object> m = toPublicMap();
        m.put("email", email);
        m.put("role", role.name());
        m.put("hasProfile", hasProfile());
        return m;
    }
}
