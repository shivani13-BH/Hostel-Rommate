package com.hostelmate.api;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps "METHOD /path/{var}" patterns to handlers. */
public class Router {

    @FunctionalInterface
    public interface Handler {
        Object handle(Request req, Map<String, String> vars) throws Exception;
    }

    public enum Access { PUBLIC, STUDENT, ADMIN, ANY_USER }

    public record Match(Handler handler, Access access, Map<String, String> vars) {}

    private record Route(String method, Pattern pattern, List<String> names, Handler handler, Access access) {}

    private final List<Route> routes = new ArrayList<>();

    public void add(String method, String path, Access access, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)}").matcher(path);
        StringBuilder regex = new StringBuilder();
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(path.substring(last, m.start()))).append("(\\d+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(path.substring(last)));
        routes.add(new Route(method, Pattern.compile("^" + regex + "$"), names, handler, access));
    }

    /** @return the match, or null. Throws 405 if the path exists for another method. */
    public Match find(String method, String path) {
        boolean pathExists = false;
        for (Route r : routes) {
            Matcher m = r.pattern.matcher(path);
            if (!m.matches()) continue;
            pathExists = true;
            if (!r.method.equals(method)) continue;
            Map<String, String> vars = new HashMap<>();
            for (int i = 0; i < r.names.size(); i++) vars.put(r.names.get(i), m.group(i + 1));
            return new Match(r.handler, r.access, vars);
        }
        if (pathExists) throw new ApiException(405, "Method " + method + " is not allowed here.");
        return null;
    }
}
