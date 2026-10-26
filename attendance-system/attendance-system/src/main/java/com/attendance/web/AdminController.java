package com.attendance.web;

import com.attendance.domain.Role;
import com.attendance.dto.Requests.BulkPersons;
import com.attendance.dto.Requests.CreatePerson;
import com.attendance.dto.Requests.CreateRoom;
import com.attendance.dto.Requests.CreateUser;
import com.attendance.dto.Requests.UpdateCapacity;
import com.attendance.dto.Responses.PersonResponse;
import com.attendance.dto.Responses.RoomResponse;
import com.attendance.dto.Responses.UserResponse;
import com.attendance.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoints. SecurityConfig restricts /api/admin/** to the ADMIN role. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    // ---- users
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUser body) {
        return admin.createUser(body);
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers(@RequestParam(required = false) Role role) {
        return admin.listUsers(role);
    }

    // ---- rooms
    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody CreateRoom body) {
        return admin.createRoom(body);
    }

    @GetMapping("/rooms")
    public List<RoomResponse> listRooms() {
        return admin.listRooms();
    }

    @PutMapping("/rooms/{roomId}/capacity")
    public void updateCapacity(@PathVariable Long roomId, @Valid @RequestBody UpdateCapacity body) {
        admin.updateCapacity(roomId, body.capacity());
    }

    // ---- persons
    @PostMapping("/rooms/{roomId}/persons")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse addPerson(@PathVariable Long roomId, @Valid @RequestBody CreatePerson body) {
        return admin.addPerson(roomId, body);
    }

    @PostMapping("/rooms/{roomId}/persons/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PersonResponse> addPersons(@PathVariable Long roomId, @Valid @RequestBody BulkPersons body) {
        return admin.addPersons(roomId, body);
    }

    @GetMapping("/rooms/{roomId}/persons")
    public List<PersonResponse> listPersons(@PathVariable Long roomId) {
        return admin.listPersons(roomId);
    }

    @PutMapping("/persons/{personId}/room/{roomId}")
    public PersonResponse movePerson(@PathVariable Long personId, @PathVariable Long roomId) {
        return admin.movePerson(personId, roomId);
    }

    // ---- observer assignment
    @PostMapping("/rooms/{roomId}/observers/{observerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void assignObserver(@PathVariable Long roomId, @PathVariable Long observerId) {
        admin.assignObserver(roomId, observerId);
    }

    @DeleteMapping("/rooms/{roomId}/observers/{observerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignObserver(@PathVariable Long roomId, @PathVariable Long observerId) {
        admin.unassignObserver(roomId, observerId);
    }

    @GetMapping("/rooms/{roomId}/observers")
    public List<UserResponse> listRoomObservers(@PathVariable Long roomId) {
        return admin.listRoomObservers(roomId);
    }
}
