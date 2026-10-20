package com.hostelmate.database;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public int id;
    public int fromId;
    public int toId;
    public String text;
    public long sentAt = System.currentTimeMillis();
    public boolean read = false;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("fromId", fromId);
        m.put("toId", toId);
        m.put("text", text);
        m.put("sentAt", sentAt);
        m.put("read", read);
        return m;
    }
}
