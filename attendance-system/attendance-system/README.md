# Multi-Room Attendance System

Spring Boot (Java 17) REST backend + MySQL with triggers.

- **Admin** creates rooms, decides how many persons each room holds, allocates persons, assigns observers, and reads the attendance report.
- **Observer** logs in and marks persons in *their assigned rooms* as **Present / Absent / Late**, in any order, at any time.
- Every mark stores **person, room, date and observer**. The admin report updates instantly.

This guide is written in build order. Follow Steps 1-10 to understand (or rebuild) the project.

---

## Step 1 - Turn the requirement into rules

| # | Rule | Enforced where |
|---|------|----------------|
| 1 | Only Admin creates rooms, persons, observers and assignments | Spring Security (`/api/admin/**`) |
| 2 | A room never holds more persons than its capacity | Trigger `trg_persons_bi`, `trg_persons_bu` |
| 3 | Capacity can't be cut below the persons already in the room | Trigger `trg_rooms_bu` |
| 4 | Only users with role OBSERVER can be assigned to rooms | Trigger `trg_room_observers_bi` |
| 5 | An observer can mark only persons of rooms assigned to them | Service check **and** trigger `trg_attendance_bi` / `trg_attendance_bu` |
| 6 | Status is Present, Absent or Late | `CHECK` constraint + Java enum |
| 7 | One record per person per date | `UNIQUE (person_id, attendance_date)` |
| 8 | The room on an attendance row can't be forged | Trigger copies it from the person |
| 9 | No attendance for future dates | Trigger `trg_attendance_bi` |
| 10 | Attendance can't be deleted; every change is logged | Triggers `trg_attendance_bd`, `trg_attendance_ai`, `trg_attendance_au` |

Why triggers *and* Java checks? Java gives friendly errors and security by user. Triggers guarantee the rules even if someone writes SQL directly or another app uses the same database.

## Step 2 - Design the database

```
users ───────────────┐
 id, username,        │ (observer_id)
 password_hash,       ▼
 full_name, role   room_observers ──────► rooms
 active              room_id, observer_id    id, room_no (unique), capacity
   ▲                                          ▲
   │ observer_id                              │ room_id
   │                                          │
attendance ───────► persons ──────────────────┘
 id, person_id,       id, person_code (unique),
 room_id, observer_id,  full_name, room_id
 attendance_date, status,
 marked_at, updated_at       attendance_audit  (filled only by triggers)
 UNIQUE(person_id, date)      old_status, new_status, action, changed_at
```

- `room_observers` is many-to-many: a room can have several observers, an observer can watch several rooms.
- `attendance` has `room_id` *and* `person_id` on purpose: if a person is later moved to another room, old attendance keeps its original room.
- Status columns are `VARCHAR + CHECK` (not `ENUM`) so Hibernate maps them without surprises.

## Step 3 - Create the database

Requirements: **MySQL 8.0.16+** (older versions silently ignore `CHECK`), JDK 17+, Maven 3.9+.

```bash
mysql -u root -p < sql/01_schema.sql        # tables + app DB user (attendance_app / change_me)
mysql -u root -p < sql/02_triggers.sql      # all triggers
mysql -u root -p < sql/03_report_procedure.sql
```

Use the `mysql` command line or MySQL Workbench (File > Open SQL Script > run). Triggers and the procedure use `DELIMITER`, a client command, so they cannot run through JDBC. To start over while developing: `mysql -u root -p < sql/00_reset_dev_only.sql`, then run 01-03 again.

Change the `attendance_app` password in `01_schema.sql` before real use, and keep it equal to `spring.datasource.password`.

## Step 4 - Triggers

| Trigger | Fires | What it does |
|---------|-------|--------------|
| `trg_rooms_bu` | before update on rooms | rejects capacity lower than current persons |
| `trg_persons_bi` | before insert on persons | rejects insert when room is full / missing |
| `trg_persons_bu` | before update on persons | same capacity check when a person moves rooms |
| `trg_room_observers_bi` | before insert on room_observers | user must exist, be active, and have role OBSERVER |
| `trg_attendance_bi` | before insert on attendance | person exists; sets `room_id` from the person; date not in future; observer assigned to that room |
| `trg_attendance_bu` | before update on attendance | person and date immutable; observer must be assigned |
| `trg_attendance_bd` | before delete on attendance | blocks deletes |
| `trg_attendance_ai` / `_au` | after insert / update | writes a row to `attendance_audit` |

Violations use `SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '...'`. `GlobalExceptionHandler` recognises that SQLSTATE and returns the message as an HTTP 409.

Try them directly in MySQL after running the demo (Step 9):

```sql
USE attendance_db;
INSERT INTO attendance (person_id, room_id, observer_id, attendance_date, status)
VALUES (4, 1, 2, CURDATE(), 'PRESENT');   -- ERROR 1644: Observer is not assigned to this person's room
UPDATE rooms SET capacity = 1 WHERE id = 1; -- ERROR 1644: Capacity cannot be lower than ...
DELETE FROM attendance;                      -- ERROR 1644: Attendance records cannot be deleted

-- history of one person's attendance changes:
SELECT * FROM attendance_audit WHERE person_id = 2 ORDER BY id;
```

## Step 5 - The report (stored procedure)

`CALL sp_attendance_report('2026-10-25');` returns one row per room: `room_no, total_persons, present_count, absent_count, late_count, not_marked_count, absentees`.

It uses `LEFT JOIN`s so rooms with no persons or no marks still appear, and `GROUP_CONCAT` to list absentees. `not_marked_count` matters: a person nobody has marked yet is *not* the same as absent, so they are never counted as absent.

## Step 6 - Create the Spring Boot project

Generated with start.spring.io style settings: Maven, Java 17, Spring Boot 3.5.x, dependencies **Spring Web, Spring Data JPA, Spring Security, Validation, MySQL Driver** (see `pom.xml`). If you re-create it yourself, bump to the newest 3.5.x patch version.

Package layout (`src/main/java/com/attendance`):

| Package | Contents | Role |
|---------|----------|------|
| `domain` | `AppUser, Room, Person, RoomObserver, Attendance`, enums `Role, AttendanceStatus` | JPA entities mapped to the tables |
| `repository` | 5 Spring Data interfaces + `ReadRepository` | `ReadRepository` runs the joined read queries and the report procedure with `JdbcTemplate` |
| `dto` | `Requests`, `Responses` | JSON in / out, with validation annotations |
| `service` | `AdminService`, `ObserverService`, `ReportService` | business logic, `@Transactional` |
| `web` | `AdminController`, `ObserverController`, `ReportController` | REST endpoints |
| `config` | `SecurityConfig`, `AdminBootstrap` | security rules, first admin |
| `exception` | `ApiException`, `GlobalExceptionHandler` | uniform JSON errors |

Design choices worth knowing:
- Entities store foreign keys as plain ids (`roomId`) instead of `@ManyToOne` objects. No lazy-loading errors, and joined views are simple SQL.
- `ddl-auto=none`: **the SQL scripts own the schema**, Hibernate never changes it.
- Services use `saveAndFlush`, so a trigger rejection surfaces immediately inside the method and rolls the transaction back.
- "Today" comes from the database (`SELECT CURDATE()`), the same clock the trigger uses, so the two can never disagree about the date or the time zone. Make sure your MySQL server time zone is what you expect (India: `SET GLOBAL time_zone = '+05:30';` or `default-time-zone='+05:30'` in `my.ini`).

## Step 7 - Security

HTTP Basic over BCrypt-hashed passwords from the `users` table, stateless (send credentials with each call).
`/api/admin/**` needs ROLE_ADMIN, `/api/observer/**` needs ROLE_OBSERVER. On first start `AdminBootstrap` creates the first admin from `application.properties` (default `admin` / `admin123`) if no admin exists, because only an admin can create users. **Change that password** (env var `ADMIN_PASSWORD` before the first start).

Use HTTPS in any real deployment, because Basic auth sends credentials on every request. A natural upgrade is JWT login (see Step 10).

## Step 8 - Run it

```bash
export DB_USER=attendance_app DB_PASSWORD=change_me ADMIN_PASSWORD=choose-a-strong-one
mvn spring-boot:run
```
(Windows PowerShell: `$env:DB_PASSWORD="change_me"`.) The API is at `http://localhost:8080`.

### API reference

**Admin** (`/api/admin`, role ADMIN)

| Method & path | Purpose |
|---------------|---------|
| `POST /users` | create user `{username,password,fullName,role}` |
| `GET /users?role=OBSERVER` | list users |
| `POST /rooms` | create room `{roomNo,capacity}` |
| `GET /rooms` | rooms with current person count |
| `PUT /rooms/{roomId}/capacity` | change capacity `{capacity}` |
| `POST /rooms/{roomId}/persons` | add one person `{personCode,fullName}` |
| `POST /rooms/{roomId}/persons/bulk` | add many `{persons:[...]}` (all-or-nothing) |
| `GET /rooms/{roomId}/persons` | persons of a room |
| `PUT /persons/{personId}/room/{roomId}` | move a person |
| `POST /rooms/{roomId}/observers/{observerId}` | assign observer |
| `DELETE /rooms/{roomId}/observers/{observerId}` | unassign observer |
| `GET /rooms/{roomId}/observers` | observers of a room |
| `GET /reports/attendance?date=YYYY-MM-DD` | the report (date optional = today) |
| `GET /reports/attendance/csv?date=` | same report as CSV |
| `GET /attendance?date=&roomId=` | detailed rows: person, room, date, observer |

**Observer** (`/api/observer`, role OBSERVER)

| Method & path | Purpose |
|---------------|---------|
| `GET /rooms` | my assigned rooms |
| `GET /rooms/{roomId}/persons?date=` | persons with status (`NOT_MARKED` if not yet) |
| `POST /attendance` | mark or correct one person for today `{personId,status}` |
| `POST /attendance/bulk` | mark several at once `{entries:[{personId,status},...]}` |

Errors are always `{timestamp,status,error,message}`: 400 bad input, 401 not logged in, 403 wrong role or not your room, 404 not found, 409 rule violation (room full, duplicate, trigger message).

## Step 9 - Try it end to end

With the app running on a **fresh** database:

```bash
bash scripts/demo.sh
```

It creates two observers, two rooms, five persons, assigns observers, marks attendance, corrects one mark, triggers the "room full" and "not your room" errors, then prints the admin report. Expected report:

| Room No | No of persons | Present | Absent | Late | Not marked | Absentees |
|---------|---------------|---------|--------|------|------------|-----------|
| R101 | 3 | 1 | 1 | 1 | 0 | S102 - Neha Verma |
| R102 | 2 | 1 | 1 | 0 | 0 | S202 - Karan Mehta |

## Step 10 - Test checklist, limits and next steps

Check these by hand (the demo covers most):
- [ ] Add persons beyond capacity -> 409 "Room is full"
- [ ] Lower capacity under current headcount -> 409
- [ ] Assign an ADMIN user as observer -> 400
- [ ] Observer marks a person of an unassigned room -> 403
- [ ] Observer marks the same person twice -> record changes, `attendance_audit` shows both
- [ ] Observer calls an admin endpoint -> 403; no password -> 401
- [ ] Report shows unmarked persons under *Not marked*, not *Absent*

Current limits (deliberate, to keep it simple):
- Observers mark for **today only** (date comes from the server). To allow back-dating, add a `date` field to `MarkAttendance` and use it in `ObserverService`.
- No "delete room/person" endpoints, to protect history. Add soft-delete flags if needed.
- The capacity trigger counts rows without locking; two admins adding to the same room in the same millisecond could both pass. Admin-only, low risk; for strictness lock the room row with `SELECT ... FOR UPDATE`.

Good next upgrades: a web front end (React or Thymeleaf), JWT login, Excel/PDF export, bulk CSV import of persons, automated tests with Testcontainers (real MySQL, so triggers are tested), Docker Compose for MySQL + app.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `Access denied for user 'attendance_app'` | password mismatch between `01_schema.sql` and `application.properties`, or script 01 not run |
| `Table ... doesn't exist` / `Unknown database` | scripts 01-03 not run; Hibernate does not create tables |
| `PROCEDURE ... sp_attendance_report does not exist` | run `03_report_procedure.sql` |
| Triggers don't fire / capacity not enforced | script 02 not run, or run through a tool that ignores `DELIMITER` |
| `CHECK` rules ignored | MySQL older than 8.0.16 |
| 401 on every call | wrong credentials; first admin is created only if no ADMIN exists |
| Date looks a day off | MySQL server time zone differs from yours (see Step 6) |
