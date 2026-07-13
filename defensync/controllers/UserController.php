<?php
require_once __DIR__ . '/../lib/Database.php';
require_once __DIR__ . '/../lib/Request.php';
require_once __DIR__ . '/../lib/Response.php';
require_once __DIR__ . '/../lib/Auth.php';
require_once __DIR__ . '/../lib/Presenter.php';

class UserController
{
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
     *  Body: { "MON": [[420,540],[780,900]], ... } (ranges in minutes). */
    public function updateAvailability(): void
    {
        $user = Auth::requireUser();

        $db = Database::conn();
        $db->beginTransaction();
        try {
            $db->prepare('DELETE FROM user_availability WHERE user_id = ?')
                ->execute([$user['id']]);
            $ins = $db->prepare(
                'INSERT INTO user_availability (user_id, weekday, start_min, end_min) VALUES (?, ?, ?, ?)'
            );
            foreach (self::sanitize(Request::body()) as $weekday => $ranges) {
                foreach ($ranges as $r) {
                    $ins->execute([$user['id'], $weekday, $r[0], $r[1]]);
                }
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
        $days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
        $clean = [];
        foreach ($availability as $day => $ranges) {
            $day = strtoupper((string) $day);
            if (!in_array($day, $days, true) || !is_array($ranges)) {
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
        return $clean;
    }
}
