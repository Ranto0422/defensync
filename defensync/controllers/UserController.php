<?php
require_once __DIR__ . '/../lib/Database.php';
require_once __DIR__ . '/../lib/Request.php';
require_once __DIR__ . '/../lib/Response.php';
require_once __DIR__ . '/../lib/Auth.php';
require_once __DIR__ . '/../lib/Presenter.php';

class UserController
{
    private const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

    /** All professors a student can invite as adviser or panelist. */
    public function professors(): void
    {
        Auth::requireUser();
        $stmt = Database::conn()->query(
            "SELECT * FROM users WHERE role = 'PROFESSOR' ORDER BY name"
        );
        Response::json(array_map(fn($row) => Presenter::user($row), $stmt->fetchAll()));
    }

    /** All students (for adding co-students to a group). */
    public function students(): void
    {
        Auth::requireUser();
        $stmt = Database::conn()->query(
            "SELECT * FROM users WHERE role = 'STUDENT' ORDER BY name"
        );
        Response::json(array_map(fn($row) => Presenter::user($row), $stmt->fetchAll()));
    }

    public function getAvailability(): void
    {
        $user = Auth::requireUser();
        Response::json(['availability' => Presenter::userAvailability((int) $user['id'])]);
    }

    /** Replace the current user's default weekly availability.
     *  Body: { "MON": [[420,540],[780,900]], ... } (ranges in minutes).
     *
     *  Existing group availability is refreshed only when it still matches the old default.
     *  If someone already edited their schedule inside that group, the group copy is left alone.
     */
    public function updateAvailability(): void
    {
        $user = Auth::requireUser();
        $userId = (int) $user['id'];
        $availability = self::sanitize(Request::body());

        $db = Database::conn();
        $db->beginTransaction();
        try {
            $oldAvailability = self::loadUserAvailability($db, $userId);
            $linkedDefenseIds = self::linkedDefenseIds($db, $userId, $oldAvailability);

            $db->prepare('DELETE FROM user_availability WHERE user_id = ?')
                ->execute([$userId]);
            self::insertUserAvailability($db, $userId, $availability);

            foreach ($linkedDefenseIds as $defenseId) {
                $db->prepare('DELETE FROM defense_availability WHERE defense_id = ? AND user_id = ?')
                    ->execute([$defenseId, $userId]);
                self::insertDefenseAvailability($db, $defenseId, $userId, $availability);
            }

            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not save availability', 500);
        }

        Response::json(['availability' => Presenter::userAvailability((int) $user['id'])]);
    }

    /** Keep only valid weekdays and [start,end] ranges (minutes) to protect the DB. */
    public static function sanitize(array $availability): array
    {
        return self::normalizeAvailability($availability);
    }

    private static function loadUserAvailability(PDO $db, int $userId): array
    {
        $stmt = $db->prepare(
            'SELECT weekday, start_min, end_min FROM user_availability
             WHERE user_id = ? ORDER BY weekday, start_min, end_min'
        );
        $stmt->execute([$userId]);
        return self::availabilityFromRows($stmt->fetchAll());
    }

    private static function linkedDefenseIds(PDO $db, int $userId, array $oldAvailability): array
    {
        $defenseIds = self::participantDefenseIds($db, $userId);
        $byDefense = self::defenseAvailabilityByDefense($db, $userId);
        $linked = [];

        foreach ($defenseIds as $defenseId) {
            $groupAvailability = $byDefense[$defenseId] ?? [];
            if ($groupAvailability === $oldAvailability) {
                $linked[] = $defenseId;
            }
        }

        return $linked;
    }

    private static function participantDefenseIds(PDO $db, int $userId): array
    {
        $stmt = $db->prepare(
            'SELECT id AS defense_id FROM defenses WHERE adviser_id = ?
             UNION
             SELECT defense_id FROM defense_students WHERE student_id = ?
             UNION
             SELECT defense_id FROM defense_panelists WHERE professor_id = ?'
        );
        $stmt->execute([$userId, $userId, $userId]);
        return array_map('intval', array_column($stmt->fetchAll(), 'defense_id'));
    }

    private static function defenseAvailabilityByDefense(PDO $db, int $userId): array
    {
        $stmt = $db->prepare(
            'SELECT defense_id, weekday, start_min, end_min FROM defense_availability
             WHERE user_id = ? ORDER BY defense_id, weekday, start_min, end_min'
        );
        $stmt->execute([$userId]);

        $byDefense = [];
        foreach ($stmt->fetchAll() as $row) {
            $defenseId = (int) $row['defense_id'];
            $byDefense[$defenseId][] = $row;
        }

        foreach ($byDefense as $defenseId => $rows) {
            $byDefense[$defenseId] = self::availabilityFromRows($rows);
        }

        return $byDefense;
    }

    private static function availabilityFromRows(array $rows): array
    {
        $availability = [];
        foreach ($rows as $row) {
            $availability[$row['weekday']][] = [(int) $row['start_min'], (int) $row['end_min']];
        }
        return self::normalizeAvailability($availability);
    }

    private static function insertUserAvailability(PDO $db, int $userId, array $availability): void
    {
        $ins = $db->prepare(
            'INSERT INTO user_availability (user_id, weekday, start_min, end_min) VALUES (?, ?, ?, ?)'
        );
        foreach ($availability as $weekday => $ranges) {
            foreach ($ranges as $r) {
                $ins->execute([$userId, $weekday, $r[0], $r[1]]);
            }
        }
    }

    private static function insertDefenseAvailability(PDO $db, int $defenseId, int $userId, array $availability): void
    {
        $ins = $db->prepare(
            'INSERT INTO defense_availability (defense_id, user_id, weekday, start_min, end_min)
             VALUES (?, ?, ?, ?, ?)'
        );
        foreach ($availability as $weekday => $ranges) {
            foreach ($ranges as $r) {
                $ins->execute([$defenseId, $userId, $weekday, $r[0], $r[1]]);
            }
        }
    }

    private static function normalizeAvailability(array $availability): array
    {
        $clean = [];
        foreach ($availability as $day => $ranges) {
            $day = strtoupper((string) $day);
            if (!in_array($day, self::WEEKDAYS, true) || !is_array($ranges)) {
                continue;
            }
            foreach ($ranges as $r) {
                if (!is_array($r) || count($r) < 2) {
                    continue;
                }
                $start = (int) $r[0];
                $end = (int) $r[1];
                if ($start >= 0 && $end > $start && $end <= 1440) {
                    $clean[$day][] = [$start, $end];
                }
            }
        }

        $normalized = [];
        foreach (self::WEEKDAYS as $day) {
            if (!isset($clean[$day])) {
                continue;
            }
            usort($clean[$day], fn($a, $b) => $a[0] <=> $b[0] ?: $a[1] <=> $b[1]);
            $normalized[$day] = $clean[$day];
        }

        return $normalized;
    }
}
