package com.hostelmate.api;

import com.hostelmate.engine.MatchResult;
import com.hostelmate.database.Booking;
import com.hostelmate.database.Room;
import com.hostelmate.database.User;

import java.util.*;

/** Hostel administration: occupancy overview, room management, all bookings. */
public class AdminController {
    private final AppContext ctx;
    private final BookingController bookings;

    public AdminController(AppContext ctx, BookingController bookings) {
        this.ctx = ctx;
        this.bookings = bookings;
    }

    public void register(Router r) {
        r.add("GET", "/api/admin/stats", Router.Access.ADMIN, this::stats);
        r.add("GET", "/api/admin/rooms", Router.Access.ADMIN, this::rooms);
        r.add("POST", "/api/admin/rooms", Router.Access.ADMIN, this::addRoom);
        r.add("POST", "/api/admin/rooms/{id}/toggle", Router.Access.ADMIN, this::toggleRoom);
        r.add("GET", "/api/admin/bookings", Router.Access.ADMIN, this::allBookings);
    }

    private Object stats(Request req, Map<String, String> v) {
        List<User> students = ctx.store.allUsers().stream().filter(u -> u.role == User.Role.STUDENT).toList();
        long profiles = students.stream().filter(User::hasProfile).count();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (Booking.Status s : Booking.Status.values()) byStatus.put(s.name(), 0);
        for (Booking b : ctx.store.allBookings()) byStatus.merge(b.status.name(), 1, Integer::sum);

        int rooms = 0, free = 0;
        for (Room room : ctx.store.allRooms()) {
            if (!room.active) continue;
            rooms++;
            if (ctx.store.isRoomAvailable(room.id)) free++;
        }

        // Average best-match score shows how well the student pool fits together.
        Set<Integer> housed = ctx.alreadyHoused();
        double sum = 0;
        int n = 0;
        for (User u : students) {
            if (!u.hasProfile()) continue;
            List<MatchResult> best = ctx.engine.topMatches(u, students, housed, 1);
            if (!best.isEmpty()) { sum += best.get(0).score; n++; }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("students", students.size());
        m.put("profilesCompleted", profiles);
        m.put("activeRooms", rooms);
        m.put("availableRooms", free);
        m.put("bookings", byStatus);
        m.put("messages", ctx.store.messageCount());
        m.put("averageBestMatch", n == 0 ? 0 : Math.round(sum / n));
        return m;
    }

    private Object rooms(Request req, Map<String, String> v) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Room room : ctx.store.allRooms()) {
            Map<String, Object> m = room.toMap();
            Booking active = ctx.store.allBookings().stream()
                    .filter(b -> b.roomId == room.id && b.isActive()).findFirst().orElse(null);
            m.put("status", !room.active ? "Out of service" : active == null ? "Available"
                    : active.status == Booking.Status.CONFIRMED ? "Occupied" : "Reserved");
            m.put("occupants", active == null ? null : bookings.describe(active, -1));
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> (String) m.get("number")));
        return Map.of("rooms", out);
    }

    private Object addRoom(Request req, Map<String, String> v) {
        Room room = new Room();
        room.block = req.required("block", "Block", 5).toUpperCase();
        room.number = req.required("number", "Room number", 15).toUpperCase();
        room.floor = req.integer("floor", "Floor", 0, 20);
        room.gender = req.required("gender", "Wing", 10);
        if (!room.gender.equals("Female") && !room.gender.equals("Male")) throw new ApiException(400, "Wing must be Female or Male.");
        room.monthlyRent = req.integer("monthlyRent", "Monthly rent", 500, 50000);
        room.amenities = req.str("amenities");
        for (Room existing : ctx.store.allRooms())
            if (existing.number.equalsIgnoreCase(room.number)) throw new ApiException(409, "Room " + room.number + " already exists.");
        return ctx.store.addRoom(room).toMap();
    }

    private Object toggleRoom(Request req, Map<String, String> v) {
        Room room = ctx.store.getRoom(Integer.parseInt(v.get("id")));
        if (room == null) throw new ApiException(404, "Room was not found.");
        if (room.active && !ctx.store.isRoomAvailable(room.id))
            throw new ApiException(409, "Room " + room.number + " has an active booking and can't be taken out of service.");
        room.active = !room.active;
        ctx.store.updateRoom(room);
        return room.toMap();
    }

    private Object allBookings(Request req, Map<String, String> v) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : ctx.store.allBookings()) out.add(bookings.describe(b, -1));
        return Map.of("bookings", out);
    }
}
