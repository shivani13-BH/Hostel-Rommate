package com.hostelmate.database;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Room implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public String block;       // e.g. "A"
    public String number;      // e.g. "A-104"
    public int floor;
    public String gender;      // which hostel wing the room belongs to
    public int monthlyRent;    // per student
    public String amenities;   // comma separated, e.g. "Attached bath, Balcony"
    public boolean active = true; // admin can take a room out of service

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("block", block);
        m.put("number", number);
        m.put("floor", floor);
        m.put("gender", gender);
        m.put("monthlyRent", monthlyRent);
        m.put("amenities", amenities);
        m.put("active", active);
        return m;
    }
}
