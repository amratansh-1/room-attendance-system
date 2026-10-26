package com.attendance.web;

import com.attendance.dto.Responses.AttendanceRecord;
import com.attendance.dto.Responses.ReportResponse;
import com.attendance.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports for the admin. Live: observers' marks show up here the moment they are saved. */
@RestController
@RequestMapping("/api/admin")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Room-wise summary: Room No, No of persons, present, absent, late, absentees. */
    @GetMapping("/reports/attendance")
    public ReportResponse report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.attendanceReport(date);
    }

    /** Same report as a downloadable CSV (opens in Excel). */
    @GetMapping(value = "/reports/attendance/csv", produces = "text/csv")
    public ResponseEntity<String> reportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ReportResponse report = reports.attendanceReport(date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendance-" + report.date() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(reports.toCsv(report));
    }

    /** Every attendance row (person, room, date, observer) for a date, optionally for one room. */
    @GetMapping("/attendance")
    public List<AttendanceRecord> records(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long roomId) {
        return reports.records(date, roomId);
    }
}
