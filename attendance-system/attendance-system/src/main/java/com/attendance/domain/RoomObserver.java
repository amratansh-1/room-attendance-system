package com.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Link row: this observer may mark attendance in this room. */
@Entity
@Table(name = "room_observers")
public class RoomObserver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "observer_id", nullable = false)
    private Long observerId;

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getObserverId() { return observerId; }
    public void setObserverId(Long observerId) { this.observerId = observerId; }
}
