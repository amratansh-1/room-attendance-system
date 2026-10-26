package com.attendance.repository;

import com.attendance.domain.Attendance;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByPersonIdAndAttendanceDate(Long personId, LocalDate attendanceDate);
}
