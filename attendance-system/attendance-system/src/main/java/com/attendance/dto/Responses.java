package com.attendance.dto;

import com.attendance.domain.AttendanceStatus;
import com.attendance.domain.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** JSON shapes the API returns. */
public final class Responses {

    private Responses() {
    }

    public record RoomResponse(Long id, String roomNo, int capacity, int personCount) {
    }

    public record PersonResponse(Long id, String personCode, String fullName, Long roomId) {
    }

    public record UserResponse(Long id, String username, String fullName, Role role, boolean active) {
    }

    /** What an observer sees for each person in a room. status is PRESENT/ABSENT/LATE or NOT_MARKED. */
    public record RoomPersonStatus(Long personId, String personCode, String fullName, String status) {
    }

    public record MarkResult(Long personId, String personCode, String fullName,
                             LocalDate date, AttendanceStatus status) {
    }

    /** One attendance row with person, room, date and observer (admin's detailed view). */
    public record AttendanceRecord(Long id, String roomNo, String personCode, String personName,
                                   LocalDate date, AttendanceStatus status, String observerName,
                                   LocalDateTime markedAt, LocalDateTime updatedAt) {
    }

    /** One line of the admin report: Room No | No of persons | present | absent | absentees. */
    public record RoomReportRow(String roomNo, int totalPersons, int present, int absent,
                                int late, int notMarked, List<String> absentees) {
    }

    public record ReportTotals(int rooms, int persons, int present, int absent, int late, int notMarked) {
    }

    public record ReportResponse(LocalDate date, ReportTotals totals, List<RoomReportRow> rooms) {
    }
}
