package com.attendance.dto;

import com.attendance.domain.AttendanceStatus;
import com.attendance.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** JSON bodies the API accepts. Validation annotations produce a 400 with a clear message. */
public final class Requests {

    private Requests() {
    }

    public record CreateUser(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role) {
    }

    public record CreateRoom(
            @NotBlank @Size(max = 20) String roomNo,
            @NotNull @Min(1) Integer capacity) {
    }

    public record UpdateCapacity(
            @NotNull @Min(1) Integer capacity) {
    }

    public record CreatePerson(
            @NotBlank @Size(max = 30) String personCode,
            @NotBlank @Size(max = 100) String fullName) {
    }

    public record BulkPersons(
            @NotEmpty List<@Valid CreatePerson> persons) {
    }

    public record MarkAttendance(
            @NotNull Long personId,
            @NotNull AttendanceStatus status) {
    }

    public record BulkMarkAttendance(
            @NotEmpty List<@Valid MarkAttendance> entries) {
    }
}
