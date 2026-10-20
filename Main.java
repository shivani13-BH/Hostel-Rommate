package com.hostelmate.api;

import com.hostelmate.database.MockDataGenerator;
import com.hostelmate.database.DataStore;

import java.nio.file.Path;

/**
 * MODULE 2 – Backend / API. Entry point of Hostel Roommate Finder.
 *
 * Usage: java -cp out com.hostelmate.api.Main [port]
 * Then open http://localhost:8080 in a browser.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        DataStore store = new DataStore(Path.of("data", "hostelmate.dat"));
        if (store.isEmpty()) {
            System.out.println("[seed] First run: creating rooms, an admin account and 60 mock student profiles...");
            MockDataGenerator.seed(store);
        }

        AppContext ctx = new AppContext(store);
        Router router = new Router();
        new AuthController(ctx).register(router);
        new ProfileController(ctx).register(router);
        new MessageController(ctx).register(router);
        BookingController bookings = new BookingController(ctx);
        bookings.register(router);
        new AdminController(ctx, bookings).register(router);

        ApiServer server = new ApiServer(ctx, router, Path.of("module1-frontend"));
        server.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        System.out.println();
        System.out.println("  Hostel Roommate Finder is running at http://localhost:" + port);
        System.out.println("  Admin login : " + MockDataGenerator.ADMIN_EMAIL + " / " + MockDataGenerator.ADMIN_PASSWORD);
        System.out.println("  Demo students: aarohi@hostel.edu (Female wing), aarav@hostel.edu (Male wing), password "
                + MockDataGenerator.DEMO_PASSWORD);
        System.out.println("  Press Ctrl+C to stop.");
    }
}
