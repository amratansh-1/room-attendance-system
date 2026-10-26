package com.attendance.service;

import com.attendance.dto.Responses.AttendanceRecord;
import com.attendance.dto.Responses.ReportResponse;
import com.attendance.dto.Responses.ReportTotals;
import com.attendance.dto.Responses.RoomReportRow;
import com.attendance.repository.ReadRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Service;

/** The admin's view: the room-wise report (JSON or CSV) and the detailed attendance rows. */
@Service
public class ReportService {

    private final ReadRepository read;

    public ReportService(ReadRepository read) {
        this.read = read;
    }

    /** Room No | No of persons | present | absent | late | not marked | absentees, plus totals. */
    public ReportResponse attendanceReport(LocalDate date) {
        LocalDate day = date != null ? date : read.today();
        List<RoomReportRow> rows = read.report(day);
        ReportTotals totals = new ReportTotals(
                rows.size(),
                sum(rows, RoomReportRow::totalPersons),
                sum(rows, RoomReportRow::present),
                sum(rows, RoomReportRow::absent),
                sum(rows, RoomReportRow::late),
                sum(rows, RoomReportRow::notMarked));
        return new ReportResponse(day, totals, rows);
    }

    public List<AttendanceRecord> records(LocalDate date, Long roomId) {
        LocalDate day = date != null ? date : read.today();
        return read.attendanceRecords(day, roomId);
    }

    public String toCsv(ReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Room No,No of Persons,Present,Absent,Late,Not Marked,Absentees\n");
        for (RoomReportRow r : report.rooms()) {
            sb.append(csv(r.roomNo())).append(',')
                    .append(r.totalPersons()).append(',')
                    .append(r.present()).append(',')
                    .append(r.absent()).append(',')
                    .append(r.late()).append(',')
                    .append(r.notMarked()).append(',')
                    .append(csv(String.join("; ", r.absentees())))
                    .append('\n');
        }
        return sb.toString();
    }

    private static int sum(List<RoomReportRow> rows, ToIntFunction<RoomReportRow> field) {
        return rows.stream().mapToInt(field).sum();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
