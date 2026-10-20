package com.hostelmate.api;

import com.hostelmate.database.Booking;
import com.hostelmate.database.Room;
import com.hostelmate.database.User;

import java.util.*;

/**
 * Room booking workflow:
 *   1. a student picks a room and a roommate  -> booking PENDING, room reserved
 *   2. the roommate accepts                   -> CONFIRMED, room occupied
 *      or declines                            -> DECLINED, room free again
 *   either student can cancel an active booking -> CANCELLED, room free again
 */
public class BookingController {
    private final AppContext ctx;
    private final Object lock = new Object(); // makes "check availability + reserve" atomic

    public BookingController(AppContext ctx) {
        this.ctx = ctx;
    }

    public void register(Router r) {
        r.add("GET", "/api/rooms", Router.Access.STUDENT, this::rooms);
        r.add("GET", "/api/bookings", Router.Access.STUDENT, this::myBookings);
        r.add("POST", "/api/bookings", Router.Access.STUDENT, this::create);
        r.add("POST", "/api/bookings/{id}/accept", Router.Access.STUDENT, (q, v) -> transition(q, v, "accept"));
        r.add("POST", "/api/bookings/{id}/decline", Router.Access.STUDENT, (q, v) -> transition(q, v, "decline"));
        r.add("POST", "/api/bookings/{id}/cancel", Router.Access.STUDENT, (q, v) -> transition(q, v, "cancel"));
    }

    private Object rooms(Request req, Map<String, String> v) {
        int maxRent = req.str("maxRent").isEmpty() ? Integer.MAX_VALUE : req.integer("maxRent", "Maximum rent");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Room room : ctx.store.allRooms()) {
            if (!room.active || !room.gender.equals(req.user.gender) || room.monthlyRent > maxRent) continue;
            Map<String, Object> m = room.toMap();
            m.put("available", ctx.store.isRoomAvailable(room.id));
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> !(Boolean) m.get("available"))
                .thenComparing(m -> (String) m.get("number")));
        return Map.of("rooms", out);
    }

    private Object myBookings(Request req, Map<String, String> v) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : ctx.store.bookingsFor(req.user.id)) out.add(describe(b, req.user.id));
        return Map.of("bookings", out);
    }

    private Object create(Request req, Map<String, String> v) {
        User me = req.user;
        if (!me.hasProfile()) throw new ApiException(409, "Complete your lifestyle profile before booking.");
        int roomId = req.integer("roomId", "Room");
        int partnerId = req.integer("partnerId", "Roommate");
        if (partnerId == me.id) throw new ApiException(400, "Choose someone other than yourself as your roommate.");

        User partner = ctx.requireUser(partnerId, "Roommate");
        if (partner.role != User.Role.STUDENT || !partner.hasProfile())
            throw new ApiException(400, "This student hasn't completed a profile yet.");
        if (!partner.gender.equals(me.gender)) throw new ApiException(400, "Roommates must be in the same hostel wing.");

        Room room = ctx.store.getRoom(roomId);
        if (room == null || !room.active) throw new ApiException(404, "Room was not found.");
        if (!room.gender.equals(me.gender)) throw new ApiException(400, "This room is in the other hostel wing.");

        synchronized (lock) {
            if (ctx.store.activeBookingFor(me.id) != null)
                throw new ApiException(409, "You already have an active booking. Cancel it before making a new one.");
            if (ctx.store.activeBookingFor(partner.id) != null)
                throw new ApiException(409, partner.name + " already has an active booking.");
            if (!ctx.store.isRoomAvailable(roomId))
                throw new ApiException(409, "Room " + room.number + " was just taken. Pick another room.");

            Booking b = new Booking();
            b.roomId = roomId;
            b.requesterId = me.id;
            b.partnerId = partner.id;
            b.compatibilityAtBooking = ctx.engine.score(partner, me.preferences, partner.preferences).score;
            ctx.store.addBooking(b);
            return describe(b, me.id);
        }
    }

    private Object transition(Request req, Map<String, String> v, String action) {
        synchronized (lock) {
            Booking b = ctx.store.getBooking(Integer.parseInt(v.get("id")));
            int me = req.user.id;
            if (b == null || !b.involves(me)) throw new ApiException(404, "Booking was not found.");
            switch (action) {
                case "accept", "decline" -> {
                    if (b.partnerId != me) throw new ApiException(403, "Only the invited roommate can " + action + " this request.");
                    if (b.status != Booking.Status.PENDING) throw new ApiException(409, "This request is already " + b.status.name().toLowerCase() + ".");
                    b.status = action.equals("accept") ? Booking.Status.CONFIRMED : Booking.Status.DECLINED;
                }
                case "cancel" -> {
                    if (!b.isActive()) throw new ApiException(409, "This booking is already " + b.status.name().toLowerCase() + ".");
                    b.status = Booking.Status.CANCELLED;
                }
                default -> throw new ApiException(400, "Unknown action.");
            }
            ctx.store.updateBooking(b);
            return describe(b, me);
        }
    }

    Map<String, Object> describe(Booking b, int viewerId) {
        Map<String, Object> m = b.toMap();
        Room room = ctx.store.getRoom(b.roomId);
        m.put("room", room == null ? null : room.toMap());
        User requester = ctx.store.getUser(b.requesterId);
        User partner = ctx.store.getUser(b.partnerId);
        m.put("requesterName", requester == null ? "Unknown" : requester.name);
        m.put("partnerName", partner == null ? "Unknown" : partner.name);
        m.put("youAreRequester", b.requesterId == viewerId);
        return m;
    }
}
