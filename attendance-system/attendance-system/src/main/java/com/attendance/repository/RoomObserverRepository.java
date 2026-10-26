package com.attendance.repository;

import com.attendance.domain.RoomObserver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomObserverRepository extends JpaRepository<RoomObserver, Long> {

    boolean existsByRoomIdAndObserverId(Long roomId, Long observerId);

    /** Returns how many rows were deleted (0 = the assignment did not exist). */
    long deleteByRoomIdAndObserverId(Long roomId, Long observerId);
}
