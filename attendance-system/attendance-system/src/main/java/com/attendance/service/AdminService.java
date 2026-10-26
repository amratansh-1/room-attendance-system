package com.attendance.service;

import com.attendance.domain.AppUser;
import com.attendance.domain.Person;
import com.attendance.domain.Role;
import com.attendance.domain.Room;
import com.attendance.domain.RoomObserver;
import com.attendance.dto.Requests.BulkPersons;
import com.attendance.dto.Requests.CreatePerson;
import com.attendance.dto.Requests.CreateRoom;
import com.attendance.dto.Requests.CreateUser;
import com.attendance.dto.Responses.PersonResponse;
import com.attendance.dto.Responses.RoomResponse;
import com.attendance.dto.Responses.UserResponse;
import com.attendance.exception.ApiException;
import com.attendance.repository.AppUserRepository;
import com.attendance.repository.PersonRepository;
import com.attendance.repository.ReadRepository;
import com.attendance.repository.RoomObserverRepository;
import com.attendance.repository.RoomRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything the Admin does: users, rooms, persons, observer assignments.
 * The app checks the common mistakes for friendly messages; the database triggers
 * are the final guard (capacity, observer role, ...), so rules hold even for direct SQL.
 */
@Service
public class AdminService {

    private final AppUserRepository users;
    private final RoomRepository rooms;
    private final PersonRepository persons;
    private final RoomObserverRepository roomObservers;
    private final PasswordEncoder encoder;
    private final ReadRepository read;

    public AdminService(AppUserRepository users, RoomRepository rooms, PersonRepository persons,
                        RoomObserverRepository roomObservers, PasswordEncoder encoder, ReadRepository read) {
        this.users = users;
        this.rooms = rooms;
        this.persons = persons;
        this.roomObservers = roomObservers;
        this.encoder = encoder;
        this.read = read;
    }

    // ------------------------------------------------------------ users

    @Transactional
    public UserResponse createUser(CreateUser req) {
        String username = req.username().trim();
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Username already exists: " + username);
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setRole(req.role());
        user.setActive(true);
        users.saveAndFlush(user);
        return toResponse(user);
    }

    public List<UserResponse> listUsers(Role role) {
        return users.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .map(AdminService::toResponse)
                .toList();
    }

    // ------------------------------------------------------------ rooms

    @Transactional
    public RoomResponse createRoom(CreateRoom req) {
        String roomNo = req.roomNo().trim();
        if (rooms.existsByRoomNo(roomNo)) {
            throw ApiException.conflict("Room already exists: " + roomNo);
        }
        Room room = new Room();
        room.setRoomNo(roomNo);
        room.setCapacity(req.capacity());
        rooms.saveAndFlush(room);
        return new RoomResponse(room.getId(), room.getRoomNo(), room.getCapacity(), 0);
    }

    public List<RoomResponse> listRooms() {
        return read.listRooms();
    }

    /** The trigger trg_rooms_bu rejects a capacity lower than the persons already in the room. */
    @Transactional
    public void updateCapacity(Long roomId, int capacity) {
        Room room = findRoom(roomId);
        room.setCapacity(capacity);
        rooms.saveAndFlush(room);
    }

    // ------------------------------------------------------------ persons

    /** The trigger trg_persons_bi rejects the insert when the room is already full. */
    @Transactional
    public PersonResponse addPerson(Long roomId, CreatePerson req) {
        findRoom(roomId);
        return savePerson(roomId, req);
    }

    /** All-or-nothing: if one person fails (duplicate code, room full), none are saved. */
    @Transactional
    public List<PersonResponse> addPersons(Long roomId, BulkPersons req) {
        findRoom(roomId);
        List<PersonResponse> created = new ArrayList<>();
        for (CreatePerson p : req.persons()) {
            created.add(savePerson(roomId, p));
        }
        return created;
    }

    public List<PersonResponse> listPersons(Long roomId) {
        findRoom(roomId);
        return read.listPersons(roomId);
    }

    @Transactional
    public PersonResponse movePerson(Long personId, Long newRoomId) {
        Person person = persons.findById(personId)
                .orElseThrow(() -> ApiException.notFound("Person " + personId + " not found"));
        findRoom(newRoomId);
        person.setRoomId(newRoomId);
        persons.saveAndFlush(person);
        return new PersonResponse(person.getId(), person.getPersonCode(), person.getFullName(), person.getRoomId());
    }

    private PersonResponse savePerson(Long roomId, CreatePerson req) {
        String code = req.personCode().trim();
        if (persons.existsByPersonCode(code)) {
            throw ApiException.conflict("Person code already exists: " + code);
        }
        Person person = new Person();
        person.setPersonCode(code);
        person.setFullName(req.fullName().trim());
        person.setRoomId(roomId);
        persons.saveAndFlush(person);
        return new PersonResponse(person.getId(), person.getPersonCode(), person.getFullName(), person.getRoomId());
    }

    // ------------------------------------------------------------ observers <-> rooms

    @Transactional
    public void assignObserver(Long roomId, Long observerId) {
        findRoom(roomId);
        AppUser observer = users.findById(observerId)
                .orElseThrow(() -> ApiException.notFound("User " + observerId + " not found"));
        if (observer.getRole() != Role.OBSERVER) {
            throw ApiException.badRequest("User '" + observer.getUsername() + "' is not an OBSERVER");
        }
        if (roomObservers.existsByRoomIdAndObserverId(roomId, observerId)) {
            throw ApiException.conflict("Observer is already assigned to this room");
        }
        RoomObserver link = new RoomObserver();
        link.setRoomId(roomId);
        link.setObserverId(observerId);
        roomObservers.saveAndFlush(link);
    }

    @Transactional
    public void unassignObserver(Long roomId, Long observerId) {
        long deleted = roomObservers.deleteByRoomIdAndObserverId(roomId, observerId);
        if (deleted == 0) {
            throw ApiException.notFound("That observer is not assigned to this room");
        }
    }

    public List<UserResponse> listRoomObservers(Long roomId) {
        findRoom(roomId);
        return read.listObserversOfRoom(roomId);
    }

    // ------------------------------------------------------------ helpers

    private Room findRoom(Long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> ApiException.notFound("Room " + roomId + " not found"));
    }

    private static UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
    }
}
