package com.hostelmate.api;

import com.hostelmate.database.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/** HTTP server: /api/* goes to the router, everything else is served from Module 1 (module1-frontend). */
public class ApiServer {
    private final AppContext ctx;
    private final Router router;
    private final Path webRoot;
    private HttpServer server;

    public ApiServer(AppContext ctx, Router router, Path webRoot) {
        this.ctx = ctx;
        this.router = router;
        this.webRoot = webRoot.toAbsolutePath().normalize();
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleApi(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        int status = 200;
        Object body;
        try {
            Request req = new Request(ex);
            Router.Match match = router.find(req.method, req.path);
            if (match == null) throw new ApiException(404, "No such endpoint: " + req.path);
            authorize(req, match.access());
            body = match.handler().handle(req, match.vars());
        } catch (ApiException e) {
            status = e.status;
            body = Map.of("error", e.getMessage());
        } catch (NumberFormatException e) {
            status = 400;
            body = Map.of("error", "Invalid id in the address.");
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            body = Map.of("error", "Something went wrong on the server. Check the server console.");
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, status, bytes);
        System.out.printf("%s %s -> %d (%d ms)%n", ex.getRequestMethod(), ex.getRequestURI().getPath(), status,
                (System.nanoTime() - start) / 1_000_000);
    }

    private void authorize(Request req, Router.Access access) {
        if (access == Router.Access.PUBLIC) return;
        String token = req.header("X-Auth-Token");
        if (token == null || token.isBlank()) throw new ApiException(401, "Log in to continue.");
        int userId = ctx.sessions.resolve(token);
        User u = userId < 0 ? null : ctx.store.getUser(userId);
        if (u == null) throw new ApiException(401, "Your session has ended. Log in again.");
        if (access == Router.Access.STUDENT && u.role != User.Role.STUDENT)
            throw new ApiException(403, "This page is for students.");
        if (access == Router.Access.ADMIN && u.role != User.Role.ADMIN)
            throw new ApiException(403, "This page is for hostel administrators.");
        req.user = u;
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        if (p.equals("/")) p = "/index.html";
        Path file = webRoot.resolve(p.substring(1)).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) { // blocks ../ path traversal
            send(ex, 404, "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        send(ex, 200, Files.readAllBytes(file));
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, byte[] bytes) throws IOException {
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
