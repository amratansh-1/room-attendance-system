package com.attendance.web;

import com.attendance.dto.Requests.BulkMarkAttendance;
import com.attendance.dto.Requests.MarkAttendance;
import com.attendance.dto.Responses.MarkResult;
import com.attendance.dto.Responses.RoomPersonStatus;
import com.attendance.dto.Responses.RoomResponse;
import com.attendance.service.ObserverService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Observer endpoints. SecurityConfig restricts /api/observer/** to the OBSERVER role. */
@RestController
@RequestMapping("/api/observer")
public class ObserverController {

    private final ObserverService observer;

    public ObserverController(ObserverService observer) {
        this.observer = observer;
    }

    /** Rooms assigned to the logged-in observer. */
    @GetMapping("/rooms")
    public List<RoomResponse> myRooms(Authentication auth) {
        return observer.myRooms(auth.getName());
    }

    /** Persons of one assigned room with their status (default: today). */
    @GetMapping("/rooms/{roomId}/persons")
    public List<RoomPersonStatus> roomPersons(
            Authentication auth,
            @PathVariable Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return observer.roomPersons(auth.getName(), roomId, date);
    }

    /** Mark or correct one person for today. */
    @PostMapping("/attendance")
    public MarkResult mark(Authentication auth, @Valid @RequestBody MarkAttendance body) {
        return observer.mark(auth.getName(), body);
    }

    /** Mark several persons (any rooms assigned to you, any order) in one call. */
    @PostMapping("/attendance/bulk")
    public List<MarkResult> markBulk(Authentication auth, @Valid @RequestBody BulkMarkAttendance body) {
        return observer.markBulk(auth.getName(), body);
    }
}
