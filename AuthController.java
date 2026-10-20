package com.hostelmate.api;

import com.hostelmate.database.User;
import com.hostelmate.database.PasswordUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private final AppContext ctx;

    public AuthController(AppContext ctx) {
        this.ctx = ctx;
    }

    public void register(Router r) {
        r.add("POST", "/api/register", Router.Access.PUBLIC, this::signUp);
        r.add("POST", "/api/login", Router.Access.PUBLIC, this::login);
        r.add("POST", "/api/logout", Router.Access.ANY_USER, this::logout);
        r.add("GET", "/api/me", Router.Access.ANY_USER, (req, v) -> req.user.toPrivateMap());
    }

    private Object signUp(Request req, Map<String, String> v) {
        String name = req.required("name", "Name", 60);
        String email = req.required("email", "Email", 100).toLowerCase();
        String password = req.required("password", "Password", 100);
        String gender = req.required("gender", "Gender", 10);
        String course = req.required("course", "Course", 60);
        int year = req.integer("year", "Year of study", 1, 6);

        if (!EMAIL.matcher(email).matches()) throw new ApiException(400, "Enter a valid email address.");
        if (password.length() < 8) throw new ApiException(400, "Password must be at least 8 characters.");
        if (!gender.equals("Female") && !gender.equals("Male")) throw new ApiException(400, "Choose the hostel wing: Female or Male.");
        if (ctx.store.findByEmail(email) != null) throw new ApiException(409, "An account with this email already exists. Log in instead.");

        User u = new User();
        u.name = name;
        u.email = email;
        u.gender = gender;
        u.course = course;
        u.year = year;
        u.salt = PasswordUtil.newSalt();
        u.passwordHash = PasswordUtil.hash(password, u.salt);
        ctx.store.addUser(u);
        return session(u);
    }

    private Object login(Request req, Map<String, String> v) {
        String email = req.required("email", "Email", 100);
        String password = req.required("password", "Password", 100);
        User u = ctx.store.findByEmail(email);
        if (u == null || !PasswordUtil.verify(password, u.salt, u.passwordHash))
            throw new ApiException(401, "Email or password is incorrect.");
        return session(u);
    }

    private Object logout(Request req, Map<String, String> v) {
        ctx.sessions.destroy(req.header("X-Auth-Token"));
        return Map.of("ok", true);
    }

    private Map<String, Object> session(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", ctx.sessions.create(u.id));
        m.put("user", u.toPrivateMap());
        return m;
    }
}
