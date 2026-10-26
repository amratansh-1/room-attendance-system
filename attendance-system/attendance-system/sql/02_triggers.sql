-- =====================================================================
-- 02_triggers.sql  -  business rules enforced INSIDE the database
-- Run:  mysql -u root -p < sql/02_triggers.sql
-- (DELIMITER is a mysql-client command: run this with the mysql CLI or
--  MySQL Workbench, not through JDBC / spring.sql.init.)
--
-- Every rule violation raises SQLSTATE '45000' with a readable message.
-- Spring Boot catches that and returns the message to the API caller.
-- =====================================================================
USE attendance_db;

DROP TRIGGER IF EXISTS trg_rooms_bu;
DROP TRIGGER IF EXISTS trg_persons_bi;
DROP TRIGGER IF EXISTS trg_persons_bu;
DROP TRIGGER IF EXISTS trg_room_observers_bi;
DROP TRIGGER IF EXISTS trg_attendance_bi;
DROP TRIGGER IF EXISTS trg_attendance_bu;
DROP TRIGGER IF EXISTS trg_attendance_bd;
DROP TRIGGER IF EXISTS trg_attendance_ai;
DROP TRIGGER IF EXISTS trg_attendance_au;

DELIMITER $$

-- ---------------------------------------------------------------------
-- 1. rooms: capacity can never be reduced below the persons already inside
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_rooms_bu BEFORE UPDATE ON rooms
FOR EACH ROW
BEGIN
    DECLARE v_count INT DEFAULT 0;

    IF NEW.capacity <> OLD.capacity THEN
        SELECT COUNT(*) INTO v_count FROM persons WHERE room_id = OLD.id;
        IF NEW.capacity < v_count THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Capacity cannot be lower than the number of persons already in this room';
        END IF;
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 2. persons (insert): a room cannot take more persons than its capacity
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_persons_bi BEFORE INSERT ON persons
FOR EACH ROW
BEGIN
    DECLARE v_capacity INT;
    DECLARE v_count    INT DEFAULT 0;

    SELECT capacity INTO v_capacity FROM rooms WHERE id = NEW.room_id;
    IF v_capacity IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Room does not exist';
    END IF;

    SELECT COUNT(*) INTO v_count FROM persons WHERE room_id = NEW.room_id;
    IF v_count >= v_capacity THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Room is full: its capacity has been reached';
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 3. persons (update): moving a person into another room also respects capacity
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_persons_bu BEFORE UPDATE ON persons
FOR EACH ROW
BEGIN
    DECLARE v_capacity INT;
    DECLARE v_count    INT DEFAULT 0;

    IF NEW.room_id <> OLD.room_id THEN
        SELECT capacity INTO v_capacity FROM rooms WHERE id = NEW.room_id;
        IF v_capacity IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target room does not exist';
        END IF;

        SELECT COUNT(*) INTO v_count FROM persons WHERE room_id = NEW.room_id;
        IF v_count >= v_capacity THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target room is full: its capacity has been reached';
        END IF;
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 4. room_observers: only active users with role OBSERVER can be assigned
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_room_observers_bi BEFORE INSERT ON room_observers
FOR EACH ROW
BEGIN
    DECLARE v_role   VARCHAR(10);
    DECLARE v_active BOOLEAN;

    SELECT role, active INTO v_role, v_active FROM users WHERE id = NEW.observer_id;
    IF v_role IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Observer user does not exist';
    END IF;
    IF v_role <> 'OBSERVER' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only users with role OBSERVER can be assigned to a room';
    END IF;
    IF v_active = FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inactive users cannot be assigned to a room';
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 5. attendance (insert): the core integrity rules
--    - person must exist; room is copied from the person (cannot be forged)
--    - date cannot be in the future
--    - the marking user must be an OBSERVER assigned to that room
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_attendance_bi BEFORE INSERT ON attendance
FOR EACH ROW
BEGIN
    DECLARE v_person_room BIGINT;
    DECLARE v_role        VARCHAR(10);
    DECLARE v_assigned    INT DEFAULT 0;

    SELECT room_id INTO v_person_room FROM persons WHERE id = NEW.person_id;
    IF v_person_room IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Person does not exist';
    END IF;

    SET NEW.room_id = v_person_room;

    IF NEW.attendance_date > CURDATE() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attendance cannot be marked for a future date';
    END IF;

    SELECT role INTO v_role FROM users WHERE id = NEW.observer_id;
    IF v_role IS NULL OR v_role <> 'OBSERVER' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only an OBSERVER can mark attendance';
    END IF;

    SELECT COUNT(*) INTO v_assigned
      FROM room_observers
     WHERE room_id = v_person_room AND observer_id = NEW.observer_id;
    IF v_assigned = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Observer is not assigned to this person''s room';
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 6. attendance (update): who/when stay fixed; only status/observer may change,
--    and the new observer must also be assigned to the room
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_attendance_bu BEFORE UPDATE ON attendance
FOR EACH ROW
BEGIN
    DECLARE v_assigned INT DEFAULT 0;

    IF NEW.person_id <> OLD.person_id OR NEW.attendance_date <> OLD.attendance_date THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The person and the date of an attendance record cannot be changed';
    END IF;

    SET NEW.room_id = OLD.room_id;

    SELECT COUNT(*) INTO v_assigned
      FROM room_observers
     WHERE room_id = OLD.room_id AND observer_id = NEW.observer_id;
    IF v_assigned = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Observer is not assigned to this person''s room';
    END IF;
END$$

-- ---------------------------------------------------------------------
-- 7. attendance (delete): records are permanent
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_attendance_bd BEFORE DELETE ON attendance
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Attendance records cannot be deleted';
END$$

-- ---------------------------------------------------------------------
-- 8. audit trail: every first mark and every later change is logged
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_attendance_ai AFTER INSERT ON attendance
FOR EACH ROW
BEGIN
    INSERT INTO attendance_audit
        (attendance_id, person_id, room_id, observer_id, attendance_date, old_status, new_status, action)
    VALUES
        (NEW.id, NEW.person_id, NEW.room_id, NEW.observer_id, NEW.attendance_date, NULL, NEW.status, 'INSERT');
END$$

CREATE TRIGGER trg_attendance_au AFTER UPDATE ON attendance
FOR EACH ROW
BEGIN
    IF NOT (OLD.status <=> NEW.status) OR NOT (OLD.observer_id <=> NEW.observer_id) THEN
        INSERT INTO attendance_audit
            (attendance_id, person_id, room_id, observer_id, attendance_date, old_status, new_status, action)
        VALUES
            (NEW.id, NEW.person_id, NEW.room_id, NEW.observer_id, NEW.attendance_date, OLD.status, NEW.status, 'UPDATE');
    END IF;
END$$

DELIMITER ;
