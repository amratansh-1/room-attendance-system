-- =====================================================================
-- 01_schema.sql  -  tables, keys, indexes and the application DB user
-- Requires MySQL 8.0.16+ (earlier versions parse but IGNORE CHECK constraints)
-- Run:  mysql -u root -p < sql/01_schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS attendance_db CHARACTER SET utf8mb4;
USE attendance_db;

-- ---------------------------------------------------------------------
-- users : both ADMIN and OBSERVER accounts live here
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,              -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'OBSERVER'))
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- rooms : the admin decides how many rooms exist and each room's capacity
-- ---------------------------------------------------------------------
CREATE TABLE rooms (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_no    VARCHAR(20) NOT NULL,
    capacity   INT         NOT NULL,                  -- max persons the admin allows in this room
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rooms_room_no (room_no),
    CONSTRAINT chk_rooms_capacity CHECK (capacity > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- persons : the people whose attendance is tracked; each belongs to ONE room
-- ---------------------------------------------------------------------
CREATE TABLE persons (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    person_code VARCHAR(30)  NOT NULL,                -- roll number / employee id
    full_name   VARCHAR(100) NOT NULL,
    room_id     BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_persons_code (person_code),
    CONSTRAINT fk_persons_room FOREIGN KEY (room_id) REFERENCES rooms (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- room_observers : which observer may mark attendance in which room (many-to-many)
-- ---------------------------------------------------------------------
CREATE TABLE room_observers (
    id          BIGINT    NOT NULL AUTO_INCREMENT,
    room_id     BIGINT    NOT NULL,
    observer_id BIGINT    NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_room_observer (room_id, observer_id),
    CONSTRAINT fk_ro_room     FOREIGN KEY (room_id)     REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_ro_observer FOREIGN KEY (observer_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- attendance : one row per person per date. Stores person, room, date, observer.
-- ---------------------------------------------------------------------
CREATE TABLE attendance (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    person_id       BIGINT      NOT NULL,
    room_id         BIGINT      NOT NULL,             -- filled/forced by trigger from the person's room
    observer_id     BIGINT      NOT NULL,             -- who marked it
    attendance_date DATE        NOT NULL,
    status          VARCHAR(10) NOT NULL,
    marked_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_person_date (person_id, attendance_date),   -- no duplicates per day
    KEY idx_attendance_room_date (room_id, attendance_date),             -- speeds up the report
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE')),
    CONSTRAINT fk_att_person   FOREIGN KEY (person_id)   REFERENCES persons (id),
    CONSTRAINT fk_att_room     FOREIGN KEY (room_id)     REFERENCES rooms (id),
    CONSTRAINT fk_att_observer FOREIGN KEY (observer_id) REFERENCES users (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- attendance_audit : history of every mark/change (filled by triggers only)
-- No foreign keys on purpose, so history is never lost or blocked.
-- ---------------------------------------------------------------------
CREATE TABLE attendance_audit (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    attendance_id   BIGINT      NOT NULL,
    person_id       BIGINT      NOT NULL,
    room_id         BIGINT      NOT NULL,
    observer_id     BIGINT      NOT NULL,
    attendance_date DATE        NOT NULL,
    old_status      VARCHAR(10) NULL,
    new_status      VARCHAR(10) NOT NULL,
    action          VARCHAR(10) NOT NULL,             -- INSERT or UPDATE
    changed_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_person_date (person_id, attendance_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Least-privilege account used by Spring Boot (do not run the app as root).
-- CHANGE THE PASSWORD and keep it the same in application.properties.
-- ---------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'attendance_app'@'localhost' IDENTIFIED BY 'change_me';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON attendance_db.* TO 'attendance_app'@'localhost';
FLUSH PRIVILEGES;
