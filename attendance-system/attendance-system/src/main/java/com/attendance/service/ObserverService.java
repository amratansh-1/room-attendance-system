package com.attendance.service;

import com.attendance.domain.AppUser;
import com.attendance.domain.Attendance;
import com.attendance.domain.Person;
import com.attendance.dto.Requests.BulkMarkAttendance;
import com.attendance.dto.Requests.MarkAttendance;
import com.attendance.dto.Responses.MarkResult;
import com.attendance.dto.Responses.RoomPersonStatus;
import com.attendance.dto.Responses.RoomResponse;
import com.attendance.exception.ApiException;
import com.attendance.repository.AppUserRepository;
import com.attendance.repository.AttendanceRepository;
import com.attendance.repository.PersonRepository;
import com.attendance.repository.ReadRepository;
import com.attendance.repository.RoomObserverRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an Observer does: see their rooms and mark Present / Absent / Late.
 * No fixed order or time slot: any person of any assigned room can be marked at any moment.
 * The date is always "today" from the database clock, so it cannot be forged by the client.
 */
@Service
public class ObserverService {

    private final AppUserRepository users;
    private final PersonRepository persons;
    private final AttendanceRepository attendance;
    private final RoomObserverRepository roomObservers;
    private final ReadRepository read;

    public ObserverService(AppUserRepository users, PersonRepository persons,
                           AttendanceRepository attendance, RoomObserverRepository roomObservers,
                           ReadRepository read) {
        this.users = users;
        this.persons = persons;
        this.attendance = attendance;
        this.roomObservers = roomObservers;
        this.read = read;
    }

    public List<RoomResponse> myRooms(String username) {
        return read.listRoomsOfObserver(currentUser(username).getId());
    }

    public List<RoomPersonStatus> roomPersons(String username, Long roomId, LocalDate date) {
        AppUser observer = currentUser(username);
        requireAssigned(roomId, observer.getId());
        LocalDate day = date != null ? date : read.today();
        return read.personsWithStatus(roomId, day);
    }

    /** Marks (or corrects) one person for today. Calling again changes the status. */
    @Transactional
    public MarkResult mark(String username, MarkAttendance req) {
        AppUser observer = currentUser(username);
        return markOne(observer, req, read.today());
    }

    /** Marks many persons at once, all-or-nothing. */
    @Transactional
    public List<MarkResult> markBulk(String username, BulkMarkAttendance req) {
        AppUser observer = currentUser(username);
        LocalDate today = read.today();
        List<MarkResult> results = new ArrayList<>();
        for (MarkAttendance entry : req.entries()) {
            results.add(markOne(observer, entry, today));
        }
        return results;
    }

    private MarkResult markOne(AppUser observer, MarkAttendance req, LocalDate date) {
        Person person = persons.findById(req.personId())
                .orElseThrow(() -> ApiException.notFound("Person " + req.personId() + " not found"));
        requireAssigned(person.getRoomId(), observer.getId());

        Attendance row = attendance.findByPersonIdAndAttendanceDate(person.getId(), date)
                .orElseGet(() -> {
                    Attendance fresh = new Attendance();
                    fresh.setPersonId(person.getId());
                    fresh.setRoomId(person.getRoomId());
                    fresh.setAttendanceDate(date);
                    return fresh;
                });
        row.setStatus(req.status());
        row.setObserverId(observer.getId());
        attendance.saveAndFlush(row);

        return new MarkResult(person.getId(), person.getPersonCode(), person.getFullName(), date, row.getStatus());
    }

    private void requireAssigned(Long roomId, Long observerId) {
        if (!roomObservers.existsByRoomIdAndObserverId(roomId, observerId)) {
            throw ApiException.forbidden("You are not assigned to this room");
        }
    }

    private AppUser currentUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> ApiException.forbidden("Unknown user"));
    }
}
