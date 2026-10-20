package com.hostelmate.api;

import com.hostelmate.engine.MatchingEngine;
import com.hostelmate.database.Booking;
import com.hostelmate.database.User;
import com.hostelmate.database.DataStore;

import java.util.HashSet;
import java.util.Set;

/** Shared services handed to every controller. */
public class AppContext {
    public final DataStore store;
    public final SessionManager sessions = new SessionManager();
    public final MatchingEngine engine = new MatchingEngine();

    public AppContext(DataStore store) {
        this.store = store;
    }

    /** Students who already have a confirmed room and so should not be suggested any more. */
    public Set<Integer> alreadyHoused() {
        Set<Integer> ids = new HashSet<>();
        for (Booking b : store.allBookings()) {
            if (b.status == Booking.Status.CONFIRMED) {
                ids.add(b.requesterId);
                ids.add(b.partnerId);
            }
        }
        return ids;
    }

    public User requireUser(int id, String what) {
        User u = store.getUser(id);
        if (u == null) throw new ApiException(404, what + " was not found.");
        return u;
    }
}
