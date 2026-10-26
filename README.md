A role-based attendance system for multiple rooms, built with Java, Spring Boot and MySQL. The administrator decides how many rooms exist and how many persons each room holds. Observers mark attendance for their assigned rooms, and the admin sees the results in a live report.

Features
Admin: creates rooms with a capacity, adds persons, creates observer accounts, and assigns observers to rooms.
Observer: logs in and marks each person as Present, Absent or Late in their assigned rooms, in any order and at any time.
Live report: shows Room No, number of persons, Present, Absent, Late, Not marked, and the absentee names or ids. It refreshes automatically and can be downloaded as CSV or printed.
Complete records: every mark stores the person, room, date and observer.
Audit trail: every mark and later correction is logged automatically.
Web interface: simple browser screens for both roles, served directly by Spring Boot.
Tech Stack
Backend: Java 17+, Spring Boot 3, Spring Data JPA, Spring Security
Database: MySQL 8 with triggers, constraints and a stored procedure
Frontend: HTML, CSS and JavaScript
Security: role-based access (Admin / Observer) and BCrypt-hashed passwords
Database Triggers

Business rules are enforced inside the database itself, so they hold even if data is changed outside the application:

A room cannot exceed its capacity.
Only users with the OBSERVER role can be assigned to rooms.
An observer can mark only persons in rooms assigned to them.
Attendance cannot be marked for a future date or deleted.
Every insert and update on attendance is written to an audit table.
Run Locally
Install JDK 17+, Maven and MySQL 8.0.16+.
Run the scripts in sql/ in order: 01_schema.sql, 02_triggers.sql, 03_report_procedure.sql.
Start the app with mvn spring-boot:run.
Open http://localhost:8080 and log in with the default admin (admin / admin123). Change these defaults before any real use.
Project Structure
sql/           schema, triggers, report procedure
src/main/java/com/attendance/
               domain, repository, service, web, config, exception, dto
src/main/resources/static/index.html   web interface
scripts/       API demo scripts
Author

**Amratansh Dwivedi**

