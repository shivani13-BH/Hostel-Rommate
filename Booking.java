package com.hostelmate.database;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A two-person room booking. Workflow:
 * requester picks a room + partner  -> PENDING
 * partner accepts                   -> CONFIRMED (room becomes occupied)
 * partner declines                  -> DECLINED
 * either student cancels            -> CANCELLED
 */
public class Booking implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { PENDING, CONFIRMED, DECLINED, CANCELLED }

    public int id;
    public int roomId;
    public int requesterId;
    public int partnerId;
    public int compatibilityAtBooking;
    public Status status = Status.PENDING;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;

    public boolean isActive() {
        return status == Status.PENDING || status == Status.CONFIRMED;
    }

    public boolean involves(int userId) {
        return requesterId == userId || partnerId == userId;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("roomId", roomId);
        m.put("requesterId", requesterId);
        m.put("partnerId", partnerId);
        m.put("compatibility", compatibilityAtBooking);
        m.put("status", status.name());
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }
}
