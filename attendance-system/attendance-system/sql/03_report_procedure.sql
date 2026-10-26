-- =====================================================================
-- 03_report_procedure.sql  -  the admin's report, computed in SQL
-- Run:  mysql -u root -p < sql/03_report_procedure.sql
-- Try:  CALL sp_attendance_report(CURDATE());
--
-- Output columns:
--   room_no | total_persons | present_count | absent_count | late_count
--   | not_marked_count | absentees  (person codes + names joined by '||')
-- Rooms with no persons still appear (LEFT JOINs), with zeros.
-- =====================================================================
USE attendance_db;

DROP PROCEDURE IF EXISTS sp_attendance_report;

DELIMITER $$

CREATE PROCEDURE sp_attendance_report(IN p_date DATE)
BEGIN
    -- default limit (1024 chars) would silently cut long absentee lists
    SET SESSION group_concat_max_len = 1000000;

    SELECT
        r.room_no,
        COUNT(p.id)                                                  AS total_persons,
        COALESCE(SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END), 0) AS present_count,
        COALESCE(SUM(CASE WHEN a.status = 'ABSENT'  THEN 1 ELSE 0 END), 0) AS absent_count,
        COALESCE(SUM(CASE WHEN a.status = 'LATE'    THEN 1 ELSE 0 END), 0) AS late_count,
        COUNT(p.id) - COUNT(a.id)                                    AS not_marked_count,
        GROUP_CONCAT(
            CASE WHEN a.status = 'ABSENT' THEN CONCAT(p.person_code, ' - ', p.full_name) END
            ORDER BY p.person_code SEPARATOR '||'
        )                                                            AS absentees
    FROM rooms r
    LEFT JOIN persons    p ON p.room_id   = r.id
    LEFT JOIN attendance a ON a.person_id = p.id AND a.attendance_date = p_date
    GROUP BY r.id, r.room_no
    ORDER BY r.room_no;
END$$

DELIMITER ;
