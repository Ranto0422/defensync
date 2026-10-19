<?php
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/Unread.php';

// Turns database rows into the JSON shapes the Android app expects.
class Presenter
{
    public static function user(array $row): array
    {
        return [
            'id'            => (string) $row['id'],
            'name'          => $row['name'],
            'email'         => $row['email'],
            'role'          => $row['role'],
            'studentNumber' => $row['student_number'] ?? null,
            'availability'  => self::userAvailability((int) $row['id']),
        ];
    }

    /** weekday -> [[start,end], ...] ranges (minutes) for a user's default schedule. */
    public static function userAvailability(int $userId): object
    {
        $stmt = Database::conn()->prepare(
            'SELECT weekday, start_min, end_min FROM user_availability
             WHERE user_id = ? ORDER BY start_min'
        );
        $stmt->execute([$userId]);
        $map = [];
        foreach ($stmt->fetchAll() as $r) {
            $map[$r['weekday']][] = [(int) $r['start_min'], (int) $r['end_min']];
        }
        return (object) $map;
    }

    public static function defense(int $id, ?int $viewerId = null): ?array
    {
        $db = Database::conn();
        $stmt = $db->prepare(
            'SELECT d.*, s.name AS student_name, a.name AS adviser_name
             FROM defenses d
             JOIN users s ON s.id = d.student_id
             JOIN users a ON a.id = d.adviser_id
             WHERE d.id = ?'
        );
        $stmt->execute([$id]);
        $d = $stmt->fetch();
        if (!$d) {
            return null;
        }

        $pstmt = $db->prepare(
            'SELECT u.id, u.name FROM defense_panelists p
             JOIN users u ON u.id = p.professor_id
             WHERE p.defense_id = ? ORDER BY u.name'
        );
        $pstmt->execute([$id]);
        $panelists = $pstmt->fetchAll();

        $sstmt = $db->prepare(
            'SELECT u.id, u.name FROM defense_students ds
             JOIN users u ON u.id = ds.student_id
             WHERE ds.defense_id = ? ORDER BY u.name'
        );
        $sstmt->execute([$id]);
        $students = $sstmt->fetchAll();

        $astmt = $db->prepare(
            'SELECT user_id, weekday, start_min, end_min FROM defense_availability
             WHERE defense_id = ? ORDER BY start_min'
        );
        $astmt->execute([$id]);
        $availability = new stdClass();
        foreach ($astmt->fetchAll() as $r) {
            $uid = (string) $r['user_id'];
            if (!isset($availability->$uid)) {
                $availability->$uid = [];
            }
            $availability->$uid[$r['weekday']][] = [(int) $r['start_min'], (int) $r['end_min']];
        }
        // Cast each user's day map to an object so empty stays {} not [].
        foreach ($availability as $uid => $days) {
            $availability->$uid = (object) $days;
        }

        $rstmt = $db->prepare('SELECT member_id, status FROM defense_responses WHERE defense_id = ?');
        $rstmt->execute([$id]);
        $responses = new stdClass();
        foreach ($rstmt->fetchAll() as $r) {
            $mid = (string) $r['member_id'];
            $responses->$mid = $r['status'];
        }

        $slot = null;
        if ($d['slot_weekday'] !== null && $d['slot_start'] !== null) {
            $slot = ['weekday' => $d['slot_weekday'], 'start' => (int) $d['slot_start']];
        }

        $unread = 0;
        if ($viewerId !== null) {
            $unread = Unread::group($viewerId, $id);
        }

        return [
            'id'            => (string) $d['id'],
            'title'         => $d['title'],
            'studentId'     => (string) $d['student_id'],
            'studentName'   => $d['student_name'],
            'studentIds'    => array_map(fn($s) => (string) $s['id'], $students),
            'studentNames'  => array_map(fn($s) => $s['name'], $students),
            'adviserId'     => (string) $d['adviser_id'],
            'adviserName'   => $d['adviser_name'],
            'panelistIds'   => array_map(fn($p) => (string) $p['id'], $panelists),
            'panelistNames' => array_map(fn($p) => $p['name'], $panelists),
            'availability'  => $availability,
            'slot'          => $slot,
            'responses'     => $responses,
            'status'        => $d['status'],
            'createdAt'     => (int) $d['created_at'],
            'unreadMessages' => $unread,
        ];
    }
}
