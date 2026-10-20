# Hostel Roommate Finder Application System

BCA project (PBL). Team: Shivani Bhandari (lead), Poonam Bagiyal, Thakur Payal, Taniya Pundir.

Students fill in a lifestyle profile. A matching engine scores how well every pair of students would get along as roommates (0–100%) and shows each student their top 5 matches. Matched students can chat inside the app and book a hostel room together. The hostel warden gets an admin panel for rooms and bookings.

## Requirements

- JDK 17 or newer (check with `javac -version`)
- Any web browser
- No internet connection, Maven, database server or external libraries needed

## How to run

**Windows:** double-click `run.bat` (or run it from Command Prompt).
**Linux / macOS:** `./run.sh`

Then open **http://localhost:8080**. To use another port: `run.bat 9090`.

On the first run the app creates a data file at `data/hostelmate.dat` and fills it with test data: an admin account, 24 rooms and 60 mock student profiles. Delete the `data` folder to reset everything.

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Student (Female wing) | aarohi@hostel.edu | student123 |
| Student (Male wing) | aarav@hostel.edu | student123 |
| Hostel warden (admin) | admin@hostel.edu | admin12345 |

Every mock student logs in with their first name, e.g. `swati@hostel.edu`, `rahul@hostel.edu`, all with password `student123`. You can also create a new account from the login page.

### Suggested demo for the presentation

1. Log in as `aarohi@hostel.edu`, open **Matches** and expand "See the breakdown" on the top match.
2. Click **Message**, send a message.
3. Click **Book a room together**, pick a room and send the request.
4. In a private/incognito window, log in as that roommate (e.g. `swati@hostel.edu`), see the unread message and accept the booking under **Book a room**.
5. Log in as the admin to show the room as occupied and the booking in the list.

### Running the tests

**Windows:** `test.bat`  **Linux / macOS:** `./test.sh`

The tests check the matching engine: identical profiles score 100, opposite profiles score low, the score is symmetric (A→B equals B→A) for 500 random pairs, stays within 0–100 for 2000 random pairs, weights add up to 100, a large cleanliness gap is penalised, and top-5 results are sorted and correctly filtered.

### Opening in an IDE

Eclipse / IntelliJ / VS Code: open the project folder and mark these as source folders: `module2-backend-api/src`, `module3-matching-engine/src`, `module3-matching-engine/test`, `module4-database/src`. Run `com.hostelmate.api.Main`. Keep the working directory as the project folder so the server can find `module1-frontend`.

## The four modules

The code is split into the four layers from the proposal's System Architecture section. Each module is its own folder, so each team member can own one.

| Module | Folder | Java package | Contents | Proposal milestone |
|---|---|---|---|---|
| 1. Frontend | `module1-frontend/` | (HTML/CSS/JS) | `index.html`, `style.css`, `app.js`: login, profile sliders, match cards, chat, booking steps, admin panel | Milestone 4 |
| 2. Backend / API | `module2-backend-api/` | `com.hostelmate.api` | `Main` (entry point), `ApiServer`, `Router`, `Request`, `Json`, `ApiException`, `AppContext`, `SessionManager`, and the controllers `AuthController`, `ProfileController`, `MessageController`, `BookingController`, `AdminController` | Milestone 2 |
| 3. Matching Engine | `module3-matching-engine/` | `com.hostelmate.engine` | `MatchingEngine`, `MatchResult`, `FactorScore`, and `test/MatchingEngineTest` | Milestone 3 |
| 4. Database | `module4-database/` | `com.hostelmate.database` | models `User`, `Preferences`, `Room`, `Booking`, `Message`; `DataStore` (persistence); `PasswordUtil` (stored password hashing); `MockDataGenerator` (60 test profiles) | Milestones 1 and 5 |

How the modules depend on each other (arrows point to what is used):

```
 Module 1  Frontend  ──HTTP/JSON──►  Module 2  Backend / API
                                        │            │
                                        ▼            ▼
                         Module 3  Matching Engine ──► Module 4  Database ──► data/hostelmate.dat
```

The Database module depends on nothing else, the Matching Engine uses only the Database models, and only the Backend/API talks to the frontend.

## Matching algorithm

Each factor gives a similarity from 0 to 1. Similarity × weight is added up to give the final score out of 100.

| Factor | Weight | How similarity is calculated |
|---|---|---|
| Sleep schedule | 18 | 1 − \|a − b\| / 4 on a 1–5 scale |
| Cleanliness | 16 | same, halved again when the gap is 3 or more |
| Noise tolerance | 12 | 1 − \|a − b\| / 4 |
| Study habits | 12 | 1 − \|a − b\| / 4 |
| Social level | 8 | 1 − \|a − b\| / 4 |
| Guests | 6 | 1 − \|a − b\| / 4 |
| Budget | 10 | 1 − (difference in ₹ / 3000), minimum 0 |
| Diet | 8 | table: same diet = 1.0, e.g. Vegetarian–Vegan 0.8, Vegetarian–Non-veg 0.35 |
| Smoking | 5 | same = 1.0, different = 0 |
| Hobbies | 5 | Jaccard overlap: shared hobbies ÷ all hobbies |
| **Total** | **100** | |

Who is shown as a match: other students in the same hostel wing who have completed a profile and don't already have a confirmed room. Results are sorted by score and the top 5 are returned. Each match also lists the two strongest factors and the factor that cost the most points ("Talk about").

**Example:** two students who differ by one step on sleep (0.75 × 18 = 13.5), match perfectly on everything else except having no shared hobbies (0 × 5 = 0) score 13.5 + 16 + 12 + 12 + 8 + 6 + 10 + 8 + 5 + 0 = 90.5 → **91%**.

## Booking workflow

```
Student A picks roommate B + a free room ──► PENDING (room held)
        B accepts ──► CONFIRMED (room occupied)
        B declines ──► DECLINED (room free again)
        A or B cancels ──► CANCELLED (room free again)
```

Rules enforced on the server: both students must be in the room's wing, each student can have only one active booking, and a room can't be booked twice (checked inside a lock so two requests at the same moment can't both succeed).

## REST API

All requests send form data (`application/x-www-form-urlencoded`); all responses are JSON. Errors come back as `{"error": "message"}` with a matching HTTP status code.

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | /api/register | anyone | create account |
| POST | /api/login | anyone | log in, returns token |
| POST | /api/logout | logged in | end session |
| GET | /api/me | logged in | current user |
| GET | /api/options | anyone | diet and hobby choices |
| POST | /api/profile | student | save lifestyle profile |
| GET | /api/matches | student | top 5 matches with breakdown |
| GET | /api/users/{id} | student | profile + compatibility with me |
| GET | /api/conversations | student | chat list with unread counts |
| GET | /api/messages/{userId} | student | conversation (marks as read) |
| POST | /api/messages/{userId} | student | send message |
| GET | /api/rooms?maxRent= | student | rooms in my wing with availability |
| GET | /api/bookings | student | my bookings |
| POST | /api/bookings | student | request a room (roomId, partnerId) |
| POST | /api/bookings/{id}/accept · decline · cancel | student | booking workflow |
| GET | /api/admin/stats | admin | dashboard numbers |
| GET / POST | /api/admin/rooms | admin | list / add rooms |
| POST | /api/admin/rooms/{id}/toggle | admin | take room out of / back into service |
| GET | /api/admin/bookings | admin | all bookings |

## Security

- Passwords hashed with PBKDF2-HMAC-SHA256 (65,536 iterations, random salt); compared in constant time.
- Random 192-bit session tokens that expire after 8 hours.
- Role checks on every API route (student vs admin).
- Server-side validation of every input; request size limit of 64 KB.
- All user text is HTML-escaped in the browser; static file serving blocks `../` path tricks.
- Students' emails are never shown to other students.

## Assumptions and limitations

- Students fill in their profiles honestly; the warden keeps room data correct.
- Single hostel/campus pilot; rooms are for two students.
- Chat refreshes every 4 seconds (polling) rather than using WebSockets.
- Data is stored in one file, which is fine for a pilot of a few thousand students.

## Future scope

- Move to **Spring Boot + MySQL**: in Module 4 replace `DataStore` with JPA repositories; in Module 2 replace the controllers with `@RestController` classes. Module 3 (matching engine) can be reused as it is.
- Let students set "deal-breakers" (e.g. must be non-smoker) and adjust factor weights.
- Learn better weights from roommate satisfaction surveys after move-in.
- Email notifications for booking requests; WebSocket chat.

## References

- Hostel Roommate Finder Application System, PBL project proposal (team document)
- Java SE 17 API documentation: https://docs.oracle.com/en/java/javase/17/docs/api/
- `com.sun.net.httpserver` package documentation (JDK)
- Jaccard index, used for hobby similarity
