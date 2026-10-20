package com.hostelmate.database;


import java.util.*;

/**
 * Fills an empty database with an admin account, hostel rooms and 60 mock
 * student profiles (Milestone 5: test data for validating the matching engine).
 * A fixed random seed makes the data the same on every machine.
 */
public final class MockDataGenerator {
    public static final String DEMO_PASSWORD = "student123";
    public static final String ADMIN_EMAIL = "admin@hostel.edu";
    public static final String ADMIN_PASSWORD = "admin12345";

    private static final String[] FEMALE = {"Aarohi", "Ananya", "Bhavna", "Diya", "Gauri", "Isha", "Jyoti", "Kavya",
            "Kritika", "Megha", "Neha", "Nisha", "Pooja", "Prachi", "Riya", "Sakshi", "Sanjana", "Shreya", "Simran",
            "Sneha", "Tanvi", "Trisha", "Vaishnavi", "Yashika", "Aditi", "Komal", "Muskan", "Pallavi", "Radhika", "Swati"};
    private static final String[] MALE = {"Aarav", "Abhishek", "Aditya", "Akash", "Aman", "Ankit", "Arjun", "Deepak",
            "Gaurav", "Harsh", "Ishaan", "Karan", "Kunal", "Manish", "Mohit", "Nikhil", "Pranav", "Rahul", "Rohit",
            "Sahil", "Saurabh", "Shubham", "Siddharth", "Tushar", "Varun", "Vikas", "Vivek", "Yash", "Ayush", "Dev"};
    private static final String[] SURNAMES = {"Bisht", "Negi", "Rawat", "Joshi", "Pant", "Bhatt", "Chauhan", "Thapa",
            "Sharma", "Verma", "Gupta", "Semwal", "Uniyal", "Dobhal", "Kandari", "Rana", "Panwar", "Nautiyal"};
    private static final String[] COURSES = {"BCA", "BBA", "B.Tech CSE", "B.Sc", "B.Com", "MCA", "BA"};
    private static final String[] BIOS = {
            "Morning person, tea before everything.", "Usually up late finishing assignments.",
            "Love a tidy room and a quiet evening.", "Always up for a chat or a cricket match.",
            "Gym in the morning, library in the evening.", "Movie nights on weekends, studies on weekdays.",
            "Prefer to keep to myself but happy to share snacks.", "Learning guitar, sorry in advance."};

    private MockDataGenerator() {}

    public static void seed(DataStore store) {
        Random rnd = new Random(42);

        User admin = new User();
        admin.name = "Hostel Warden";
        admin.email = ADMIN_EMAIL;
        admin.gender = "Female";
        admin.course = "Administration";
        admin.year = 1;
        admin.role = User.Role.ADMIN;
        admin.salt = PasswordUtil.newSalt();
        admin.passwordHash = PasswordUtil.hash(ADMIN_PASSWORD, admin.salt);
        store.addUser(admin);

        // Two wings, two blocks each, 6 rooms per block.
        String[][] blocks = {{"A", "Female"}, {"B", "Female"}, {"C", "Male"}, {"D", "Male"}};
        String[] amenities = {"Attached bath", "Balcony", "Study tables", "Wardrobe", "Ceiling fan", "Geyser"};
        for (String[] blk : blocks) {
            for (int floor = 1; floor <= 2; floor++) {
                for (int n = 1; n <= 3; n++) {
                    Room r = new Room();
                    r.block = blk[0];
                    r.gender = blk[1];
                    r.floor = floor;
                    r.number = blk[0] + "-" + floor + "0" + n;
                    r.monthlyRent = 4000 + 500 * rnd.nextInt(7); // 4000 .. 7000
                    List<String> a = new ArrayList<>(List.of(amenities));
                    Collections.shuffle(a, rnd);
                    r.amenities = String.join(", ", a.subList(0, 2 + rnd.nextInt(3)));
                    store.addRoom(r);
                }
            }
        }

        // One salt + hash shared by mock accounts keeps start-up fast; real sign-ups each get their own salt.
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(DEMO_PASSWORD, salt);
        for (int i = 0; i < 60; i++) {
            boolean female = i % 2 == 0;
            String first = (female ? FEMALE : MALE)[i / 2];
            String last = SURNAMES[rnd.nextInt(SURNAMES.length)];
            User u = new User();
            u.name = first + " " + last;
            u.email = first.toLowerCase() + "@hostel.edu"; // e.g. aarohi@hostel.edu
            u.gender = female ? "Female" : "Male";
            u.course = COURSES[rnd.nextInt(COURSES.length)];
            u.year = 1 + rnd.nextInt(3);
            u.salt = salt;
            u.passwordHash = hash;
            u.preferences = randomPreferences(rnd);
            store.addUser(u);
        }
    }

    /** Correlated random profile: night owls tend to tolerate more noise, etc. – closer to real people. */
    static Preferences randomPreferences(Random rnd) {
        Preferences p = new Preferences();
        p.sleepSchedule = 1 + rnd.nextInt(5);
        p.noiseTolerance = Preferences.clampScale(p.sleepSchedule + rnd.nextInt(3) - 1);
        p.socialLevel = 1 + rnd.nextInt(5);
        p.guestFrequency = Preferences.clampScale(p.socialLevel + rnd.nextInt(3) - 1);
        p.studyHabit = Preferences.clampScale((p.socialLevel + p.noiseTolerance) / 2 + rnd.nextInt(3) - 1);
        p.cleanliness = 1 + rnd.nextInt(5);
        p.budget = 4000 + 500 * rnd.nextInt(9);
        double d = rnd.nextDouble();
        p.diet = d < 0.45 ? "Vegetarian" : d < 0.75 ? "Non-vegetarian" : d < 0.88 ? "Eggetarian" : d < 0.95 ? "Jain" : "Vegan";
        p.smoker = rnd.nextDouble() < 0.1;
        List<String> h = new ArrayList<>(List.of(Preferences.HOBBY_OPTIONS));
        Collections.shuffle(h, rnd);
        p.hobbies.addAll(h.subList(0, 2 + rnd.nextInt(3)));
        p.bio = BIOS[rnd.nextInt(BIOS.length)];
        return p;
    }
}
