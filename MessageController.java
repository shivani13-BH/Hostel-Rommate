package com.hostelmate.api;

import com.hostelmate.database.Message;
import com.hostelmate.database.User;

import java.util.*;

public class MessageController {
    private final AppContext ctx;

    public MessageController(AppContext ctx) {
        this.ctx = ctx;
    }

    public void register(Router r) {
        r.add("GET", "/api/conversations", Router.Access.STUDENT, this::conversations);
        r.add("GET", "/api/messages/{userId}", Router.Access.STUDENT, this::thread);
        r.add("POST", "/api/messages/{userId}", Router.Access.STUDENT, this::send);
    }

    private Object conversations(Request req, Map<String, String> v) {
        int me = req.user.id;
        Map<Integer, Message> last = new HashMap<>();
        Map<Integer, Integer> unread = new HashMap<>();
        for (Message m : ctx.store.messagesInvolving(me)) {
            int other = m.fromId == me ? m.toId : m.fromId;
            Message prev = last.get(other);
            if (prev == null || m.sentAt >= prev.sentAt) last.put(other, m);
            if (m.toId == me && !m.read) unread.merge(other, 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        last.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sentAt, a.getValue().sentAt))
                .forEach(e -> {
                    User other = ctx.store.getUser(e.getKey());
                    if (other == null) return;
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("user", Map.of("id", other.id, "name", other.name, "course", other.course));
                    c.put("lastMessage", e.getValue().toMap());
                    c.put("unread", unread.getOrDefault(e.getKey(), 0));
                    out.add(c);
                });
        return Map.of("conversations", out);
    }

    private User partner(Request req, Map<String, String> v) {
        User other = ctx.requireUser(Integer.parseInt(v.get("userId")), "Student");
        if (other.role != User.Role.STUDENT || other.id == req.user.id)
            throw new ApiException(400, "You can only message other students.");
        return other;
    }

    private Object thread(Request req, Map<String, String> v) {
        User other = partner(req, v);
        List<Map<String, Object>> msgs = new ArrayList<>();
        for (Message m : ctx.store.conversation(req.user.id, other.id)) msgs.add(m.toMap());
        return Map.of("user", Map.of("id", other.id, "name", other.name, "course", other.course), "messages", msgs);
    }

    private Object send(Request req, Map<String, String> v) {
        User other = partner(req, v);
        String text = req.required("text", "Message", 1000);
        Message m = new Message();
        m.fromId = req.user.id;
        m.toId = other.id;
        m.text = text;
        return ctx.store.addMessage(m).toMap();
    }
}
