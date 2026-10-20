package com.hostelmate.database;


import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistence layer. Keeps all records in memory for fast matching and writes
 * a snapshot to disk after every change (atomic replace, so a crash never
 * leaves a half-written file). All public methods are synchronized because the
 * HTTP server handles requests on several threads.
 *
 * To move to MySQL later, re-implement these same methods with JDBC –
 * nothing else in the project needs to change.
 */
public class DataStore {

    private static class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        Map<Integer, User> users = new LinkedHashMap<>();
        Map<Integer, Room> rooms = new LinkedHashMap<>();
        Map<Integer, Booking> bookings = new LinkedHashMap<>();
        List<Message> messages = new ArrayList<>();
        int nextUserId = 1, nextRoomId = 1, nextBookingId = 1, nextMessageId = 1;
    }

    private final Path file;
    private Snapshot data = new Snapshot();

    public DataStore(Path file) {
        this.file = file;
        load();
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (!Files.exists(file)) return;
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            data = (Snapshot) in.readObject();
            System.out.println("[store] Loaded " + data.users.size() + " users, " + data.rooms.size()
                    + " rooms, " + data.bookings.size() + " bookings from " + file);
        } catch (Exception e) {
            System.err.println("[store] Could not read " + file + " (" + e.getMessage() + "). Starting with an empty store.");
            data = new Snapshot();
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeObject(data);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[store] Save failed: " + e.getMessage());
        }
    }

    public synchronized boolean isEmpty() {
        return data.users.isEmpty();
    }

    // ---------------------------------------------------------------- users

    public synchronized User addUser(User u) {
        u.id = data.nextUserId++;
        data.users.put(u.id, u);
        save();
        return u;
    }

    public synchronized void updateUser(User u) {
        data.users.put(u.id, u);
        save();
    }

    public synchronized User getUser(int id) {
        return data.users.get(id);
    }

    public synchronized User findByEmail(String email) {
        if (email == null) return null;
        for (User u : data.users.values())
            if (u.email.equalsIgnoreCase(email.trim())) return u;
        return null;
    }

    public synchronized List<User> allUsers() {
        return new ArrayList<>(data.users.values());
    }

    // ---------------------------------------------------------------- rooms

    public synchronized Room addRoom(Room r) {
        r.id = data.nextRoomId++;
        data.rooms.put(r.id, r);
        save();
        return r;
    }

    public synchronized void updateRoom(Room r) {
        data.rooms.put(r.id, r);
        save();
    }

    public synchronized Room getRoom(int id) {
        return data.rooms.get(id);
    }

    public synchronized List<Room> allRooms() {
        return new ArrayList<>(data.rooms.values());
    }

    /** A room is available if it is active and has no pending/confirmed booking. */
    public synchronized boolean isRoomAvailable(int roomId) {
        Room r = data.rooms.get(roomId);
        if (r == null || !r.active) return false;
        return data.bookings.values().stream().noneMatch(b -> b.roomId == roomId && b.isActive());
    }

    // ---------------------------------------------------------------- bookings

    public synchronized Booking addBooking(Booking b) {
        b.id = data.nextBookingId++;
        data.bookings.put(b.id, b);
        save();
        return b;
    }

    public synchronized void updateBooking(Booking b) {
        b.updatedAt = System.currentTimeMillis();
        data.bookings.put(b.id, b);
        save();
    }

    public synchronized Booking getBooking(int id) {
        return data.bookings.get(id);
    }

    public synchronized List<Booking> bookingsFor(int userId) {
        return data.bookings.values().stream().filter(b -> b.involves(userId))
                .sorted(Comparator.comparingLong((Booking b) -> b.createdAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized Booking activeBookingFor(int userId) {
        return data.bookings.values().stream().filter(b -> b.involves(userId) && b.isActive())
                .findFirst().orElse(null);
    }

    public synchronized List<Booking> allBookings() {
        List<Booking> list = new ArrayList<>(data.bookings.values());
        list.sort(Comparator.comparingLong((Booking b) -> b.createdAt).reversed());
        return list;
    }

    // ---------------------------------------------------------------- messages

    public synchronized Message addMessage(Message m) {
        m.id = data.nextMessageId++;
        data.messages.add(m);
        save();
        return m;
    }

    /** Conversation between two users, oldest first; marks messages sent to {@code me} as read. */
    public synchronized List<Message> conversation(int me, int other) {
        boolean changed = false;
        List<Message> out = new ArrayList<>();
        for (Message m : data.messages) {
            if ((m.fromId == me && m.toId == other) || (m.fromId == other && m.toId == me)) {
                if (m.toId == me && !m.read) { m.read = true; changed = true; }
                out.add(m);
            }
        }
        if (changed) save();
        return out;
    }

    public synchronized List<Message> messagesInvolving(int userId) {
        return data.messages.stream().filter(m -> m.fromId == userId || m.toId == userId)
                .collect(Collectors.toList());
    }

    public synchronized int messageCount() {
        return data.messages.size();
    }
}
