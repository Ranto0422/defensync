-- DefenSync database schema.
-- Import once via phpMyAdmin (Import tab) or: mysql -u root < schema.sql

CREATE DATABASE IF NOT EXISTS defensync
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE defensync;

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('STUDENT','PROFESSOR') NOT NULL DEFAULT 'STUDENT',
    student_number VARCHAR(50) NULL UNIQUE,   -- students only; searchable when adding co-students
    api_token     VARCHAR(64) NULL,
    created_at    BIGINT NOT NULL,
    INDEX idx_users_token (api_token),
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- A user's default weekly availability, stored as time ranges in minutes-from-midnight
-- (e.g. 7:00 AM = 420, 9:00 PM = 1260). A day can have several non-overlapping ranges.
CREATE TABLE IF NOT EXISTS user_availability (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id   INT NOT NULL,
    weekday   VARCHAR(3) NOT NULL,
    start_min INT NOT NULL,
    end_min   INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_avail (user_id, weekday)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS defenses (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    student_id   INT NOT NULL,
    adviser_id   INT NOT NULL,
    status       ENUM('SCHEDULING','PENDING','CONFIRMED','DECLINED','CANCELLED')
                 NOT NULL DEFAULT 'SCHEDULING',
    slot_weekday VARCHAR(3) NULL,
    slot_start   INT NULL,          -- chosen defense start, minutes-from-midnight
    created_at   BIGINT NOT NULL,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (adviser_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS defense_panelists (
    defense_id   INT NOT NULL,
    professor_id INT NOT NULL,
    PRIMARY KEY (defense_id, professor_id),
    FOREIGN KEY (defense_id) REFERENCES defenses(id) ON DELETE CASCADE,
    FOREIGN KEY (professor_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- Students in a group. The creator (defenses.student_id) is always one of these; a group may
-- have several students. Student availability is optional and never gates scheduling.
CREATE TABLE IF NOT EXISTS defense_students (
    defense_id INT NOT NULL,
    student_id INT NOT NULL,
    PRIMARY KEY (defense_id, student_id),
    FOREIGN KEY (defense_id) REFERENCES defenses(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- Per-group availability ranges, seeded from each participant's default. Default schedule edits
-- refresh rows that still match the old default; group-edited rows are treated as manual overrides.
CREATE TABLE IF NOT EXISTS defense_availability (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    defense_id INT NOT NULL,
    user_id    INT NOT NULL,
    weekday    VARCHAR(3) NOT NULL,
    start_min  INT NOT NULL,
    end_min    INT NOT NULL,
    FOREIGN KEY (defense_id) REFERENCES defenses(id) ON DELETE CASCADE,
    INDEX idx_def_avail (defense_id, user_id, weekday)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS defense_responses (
    defense_id INT NOT NULL,
    member_id  INT NOT NULL,
    status     ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (defense_id, member_id),
    FOREIGN KEY (defense_id) REFERENCES defenses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Per-user read cursor for each conversation (group or DM), for unread counts.
CREATE TABLE IF NOT EXISTS chat_reads (
    user_id      INT NOT NULL,
    ctype        VARCHAR(8) NOT NULL,   -- 'GROUP' or 'DM'
    ckey         INT NOT NULL,          -- defense_id for GROUP, other user_id for DM
    last_read_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, ctype, ckey)
) ENGINE=InnoDB;

-- Private 1-on-1 direct messages between two users.
CREATE TABLE IF NOT EXISTS direct_messages (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    sender_id    INT NOT NULL,
    recipient_id INT NOT NULL,
    body         TEXT NOT NULL,
    edited       TINYINT NOT NULL DEFAULT 0,
    created_at   BIGINT NOT NULL,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (recipient_id) REFERENCES users(id),
    INDEX idx_dm_pair (sender_id, recipient_id, created_at)
) ENGINE=InnoDB;

-- Group chat: any participant (student, adviser, panelist) can post/read.
CREATE TABLE IF NOT EXISTS defense_messages (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    defense_id INT NOT NULL,
    sender_id  INT NOT NULL,
    body       TEXT NOT NULL,
    edited     TINYINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (defense_id) REFERENCES defenses(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_msg (defense_id, created_at)
) ENGINE=InnoDB;
