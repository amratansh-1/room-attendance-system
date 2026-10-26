package com.attendance.repository;

import com.attendance.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByRoomNo(String roomNo);
}
