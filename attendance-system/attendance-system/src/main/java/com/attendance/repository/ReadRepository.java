package com.attendance.repository;

import com.attendance.domain.AttendanceStatus;
import com.attendance.dto.Responses.AttendanceRecord;
import com.attendance.dto.Responses.PersonResponse;
import com.attendance.dto.Responses.RoomPersonStatus;
import com.attendance.dto.Responses.RoomReportRow;
import com.attendance.dto.Responses.RoomResponse;
import com.attendance.dto.Responses.UserResponse;
import com.attendance.domain.Role;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries that join several tables. Plain SQL is simpler and faster here
 * than JPA relations, and it returns exactly the shape the API needs.
 */
@Repository
public class ReadRepository {

    private final JdbcTemplate jdbc;

    public ReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "Today" according to the database clock, so the app and the triggers always agree. */
    public LocalDate today() {
        java.sql.Date d = jdbc.queryForObject("SELECT CURDATE()", java.sql.Date.class);
        return d.toLocalDate();
    }

    public List<RoomResponse> listRooms() {
        return jdbc.query(
                "SELECT r.id, r.room_no, r.capacity, COUNT(p.id) AS person_count "
                        + "FROM rooms r LEFT JOIN persons p ON p.room_id = r.id "
                        + "GROUP BY r.id, r.room_no, r.capacity ORDER BY r.room_no",
                (rs, i) -> new RoomResponse(rs.getLong("id"), rs.getString("room_no"),
                        rs.getInt("capacity"), rs.getInt("person_count")));
    }

    public List<RoomResponse> listRoomsOfObserver(Long observerId) {
        return jdbc.query(
                "SELECT r.id, r.room_no, r.capacity, "
                        + "(SELECT COUNT(*) FROM persons p WHERE p.room_id = r.id) AS person_count "
                        + "FROM rooms r JOIN room_observers ro ON ro.room_id = r.id "
                        + "WHERE ro.observer_id = ? ORDER BY r.room_no",
                (rs, i) -> new RoomResponse(rs.getLong("id"), rs.getString("room_no"),
                        rs.getInt("capacity"), rs.getInt("person_count")),
                observerId);
    }

    public List<PersonResponse> listPersons(Long roomId) {
        return jdbc.query(
                "SELECT id, person_code, full_name, room_id FROM persons WHERE room_id = ? ORDER BY person_code",
                (rs, i) -> new PersonResponse(rs.getLong("id"), rs.getString("person_code"),
                        rs.getString("full_name"), rs.getLong("room_id")),
                roomId);
    }

    public List<UserResponse> listObserversOfRoom(Long roomId) {
        return jdbc.query(
                "SELECT u.id, u.username, u.full_name, u.role, u.active "
                        + "FROM users u JOIN room_observers ro ON ro.observer_id = u.id "
                        + "WHERE ro.room_id = ? ORDER BY u.full_name",
                (rs, i) -> new UserResponse(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name"), Role.valueOf(rs.getString("role")),
                        rs.getBoolean("active")),
                roomId);
    }

    /** Every person of a room with their status on the given date (NOT_MARKED if no row yet). */
    public List<RoomPersonStatus> personsWithStatus(Long roomId, LocalDate date) {
        return jdbc.query(
                "SELECT p.id, p.person_code, p.full_name, a.status "
                        + "FROM persons p "
                        + "LEFT JOIN attendance a ON a.person_id = p.id AND a.attendance_date = ? "
                        + "WHERE p.room_id = ? ORDER BY p.person_code",
                (rs, i) -> {
                    String status = rs.getString("status");
                    return new RoomPersonStatus(rs.getLong("id"), rs.getString("person_code"),
                            rs.getString("full_name"), status == null ? "NOT_MARKED" : status);
                },
                java.sql.Date.valueOf(date), roomId);
    }

    /** Detailed attendance rows (person, room, date, observer) for one date, optionally one room. */
    public List<AttendanceRecord> attendanceRecords(LocalDate date, Long roomId) {
        String sql = "SELECT a.id, r.room_no, p.person_code, p.full_name AS person_name, "
                + "a.attendance_date, a.status, u.full_name AS observer_name, a.marked_at, a.updated_at "
                + "FROM attendance a "
                + "JOIN persons p ON p.id = a.person_id "
                + "JOIN rooms r ON r.id = a.room_id "
                + "JOIN users u ON u.id = a.observer_id "
                + "WHERE a.attendance_date = ? "
                + (roomId != null ? "AND a.room_id = ? " : "")
                + "ORDER BY r.room_no, p.person_code";

        org.springframework.jdbc.core.RowMapper<AttendanceRecord> mapper = (rs, i) -> new AttendanceRecord(
                rs.getLong("id"), rs.getString("room_no"), rs.getString("person_code"),
                rs.getString("person_name"), rs.getDate("attendance_date").toLocalDate(),
                AttendanceStatus.valueOf(rs.getString("status")), rs.getString("observer_name"),
                rs.getTimestamp("marked_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());

        if (roomId != null) {
            return jdbc.query(sql, mapper, java.sql.Date.valueOf(date), roomId);
        }
        return jdbc.query(sql, mapper, java.sql.Date.valueOf(date));
    }

    /** Runs the stored procedure created in sql/03_report_procedure.sql. */
    public List<RoomReportRow> report(LocalDate date) {
        return jdbc.query(
                "CALL sp_attendance_report(?)",
                (rs, i) -> new RoomReportRow(
                        rs.getString("room_no"),
                        rs.getInt("total_persons"),
                        rs.getInt("present_count"),
                        rs.getInt("absent_count"),
                        rs.getInt("late_count"),
                        rs.getInt("not_marked_count"),
                        splitAbsentees(rs.getString("absentees"))),
                java.sql.Date.valueOf(date));
    }

    private static List<String> splitAbsentees(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.asList(joined.split("\\|\\|"));
    }
}
