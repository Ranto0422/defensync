<?php
require_once __DIR__ . '/../lib/Database.php';
require_once __DIR__ . '/../lib/Request.php';
require_once __DIR__ . '/../lib/Response.php';
require_once __DIR__ . '/../lib/Auth.php';
require_once __DIR__ . '/../lib/Presenter.php';
require_once __DIR__ . '/UserController.php';

class DefenseController
{
    /** Every defense the current user is part of (student, adviser, or panelist). */
    public function index(): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        $stmt = Database::conn()->prepare(
            'SELECT DISTINCT d.id, d.created_at FROM defenses d
             WHERE d.adviser_id = ?
                OR EXISTS (SELECT 1 FROM defense_students ds WHERE ds.defense_id = d.id AND ds.student_id = ?)
                OR EXISTS (SELECT 1 FROM defense_panelists p WHERE p.defense_id = d.id AND p.professor_id = ?)
             ORDER BY d.created_at DESC'
        );
        $stmt->execute([$me, $me, $me]);
        $defenses = array_map(
            fn($row) => Presenter::defense((int) $row['id'], $me),
            $stmt->fetchAll()
        );
        Response::json($defenses);
    }

    /** Student creates a group. Body: { title, adviserId, panelistIds: [] } */
    public function create(): void
    {
        $user = Auth::requireUser();
        if ($user['role'] !== 'STUDENT') {
            Response::error('Only students can create a defense', 403);
        }
        $body = Request::body();
        $title = trim($body['title'] ?? '');
        $adviserId = (int) ($body['adviserId'] ?? 0);
        $panelistIds = array_values(array_unique(array_map('intval', $body['panelistIds'] ?? [])));
        $panelistIds = array_values(array_filter($panelistIds, fn($id) => $id !== $adviserId && $id > 0));
        // Optional co-students; the creator is always the first student.
        $coStudentIds = self::intList($body['studentIds'] ?? []);
        $coStudentIds = array_values(array_filter($coStudentIds, fn($s) => $s > 0 && $s !== (int) $user['id']));

        if ($title === '' || $adviserId <= 0 || count($panelistIds) === 0) {
            Response::error('Title, an adviser, and at least one panelist are required');
        }

        $db = Database::conn();
        $memberIds = array_merge([$adviserId], $panelistIds);
        $studentIds = array_values(array_unique(array_merge([(int) $user['id']], $coStudentIds)));
        $participantIds = array_values(array_unique(array_merge($studentIds, $memberIds)));

        $db->beginTransaction();
        try {
            $db->prepare(
                'INSERT INTO defenses (title, student_id, adviser_id, status, created_at)
                 VALUES (?, ?, ?, ?, ?)'
            )->execute([
                $title, $user['id'], $adviserId, 'SCHEDULING', (int) (microtime(true) * 1000),
            ]);
            $defenseId = (int) $db->lastInsertId();

            $insPanel = $db->prepare(
                'INSERT INTO defense_panelists (defense_id, professor_id) VALUES (?, ?)'
            );
            foreach ($panelistIds as $pid) {
                $insPanel->execute([$defenseId, $pid]);
            }

            $insResp = $db->prepare(
                'INSERT INTO defense_responses (defense_id, member_id, status) VALUES (?, ?, ?)'
            );
            foreach ($memberIds as $mid) {
                $insResp->execute([$defenseId, $mid, 'PENDING']);
            }

            // The creator plus any chosen co-students form the group's students.
            $insStudent = $db->prepare('INSERT INTO defense_students (defense_id, student_id) VALUES (?, ?)');
            foreach ($studentIds as $sid) {
                $insStudent->execute([$defenseId, $sid]);
            }

            // Seed per-group availability from each participant's default schedule.
            $placeholders = implode(',', array_fill(0, count($participantIds), '?'));
            $db->prepare(
                "INSERT INTO defense_availability (defense_id, user_id, weekday, start_min, end_min)
                 SELECT ?, user_id, weekday, start_min, end_min FROM user_availability
                 WHERE user_id IN ($placeholders)"
            )->execute(array_merge([$defenseId], $participantIds));

            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not create defense', 500);
        }

        Response::json(Presenter::defense($defenseId));
    }

    public function show(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id, (int) $user['id']);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $this->requireParticipant($defense, $user);
        Response::json($defense);
    }

    /** A participant replaces their own availability for this group. */
    public function updateAvailability(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $this->requireParticipant($defense, $user);

        $db = Database::conn();
        $db->beginTransaction();
        try {
            $db->prepare('DELETE FROM defense_availability WHERE defense_id = ? AND user_id = ?')
                ->execute([$id, $user['id']]);
            $ins = $db->prepare(
                'INSERT INTO defense_availability (defense_id, user_id, weekday, start_min, end_min)
                 VALUES (?, ?, ?, ?, ?)'
            );
            foreach (UserController::sanitize(Request::body()) as $weekday => $ranges) {
                foreach ($ranges as $r) {
                    $ins->execute([$id, $user['id'], $weekday, $r[0], $r[1]]);
                }
            }
            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not save availability', 500);
        }

        Response::json(Presenter::defense($id));
    }

    /** Student confirms an aligned slot. Body: { weekday, hour } */
    public function selectSlot(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        if ((string) $user['id'] !== $defense['studentId']) {
            Response::error('Only the student can pick the time', 403);
        }

        $body = Request::body();
        $weekday = strtoupper(trim($body['weekday'] ?? ''));
        $start = (int) ($body['start'] ?? -1);
        if (!in_array($weekday, ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'], true) || $start < 0) {
            Response::error('A valid weekday and start time are required');
        }

        $db = Database::conn();
        $db->beginTransaction();
        try {
            $db->prepare('UPDATE defenses SET slot_weekday = ?, slot_start = ?, status = ? WHERE id = ?')
                ->execute([$weekday, $start, 'PENDING', $id]);
            $db->prepare("UPDATE defense_responses SET status = 'PENDING' WHERE defense_id = ?")
                ->execute([$id]);
            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not set the time', 500);
        }

        Response::json(Presenter::defense($id));
    }

    /** A member accepts or declines. Body: { accept: true|false } */
    public function respond(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $memberIds = array_merge([$defense['adviserId']], $defense['panelistIds']);
        if (!in_array((string) $user['id'], $memberIds, true)) {
            Response::error('You are not a member of this panel', 403);
        }

        $accept = (bool) (Request::body()['accept'] ?? false);
        $db = Database::conn();
        $db->beginTransaction();
        try {
            $db->prepare('UPDATE defense_responses SET status = ? WHERE defense_id = ? AND member_id = ?')
                ->execute([$accept ? 'ACCEPTED' : 'DECLINED', $id, $user['id']]);
            $this->recomputeStatus($db, $id);
            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not record your response', 500);
        }

        Response::json(Presenter::defense($id));
    }

    public function cancel(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        if ((string) $user['id'] !== $defense['studentId']) {
            Response::error('Only the student can cancel', 403);
        }
        Database::conn()->prepare("UPDATE defenses SET status = 'CANCELLED' WHERE id = ?")
            ->execute([$id]);
        Response::json(Presenter::defense($id));
    }

    /** Permanently delete a defense (owner only, allowed at any status). */
    public function delete(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        if ((string) $user['id'] !== $defense['studentId']) {
            Response::error('Only the student who created it can delete this defense', 403);
        }
        // Child rows (panelists, students, availability, responses, messages) cascade via FKs.
        Database::conn()->prepare('DELETE FROM defenses WHERE id = ?')->execute([$id]);
        Response::json(['ok' => true]);
    }

    /** any DECLINED -> DECLINED; all ACCEPTED -> CONFIRMED; otherwise PENDING. */
    private function recomputeStatus(PDO $db, int $id): void
    {
        $stmt = $db->prepare('SELECT status FROM defense_responses WHERE defense_id = ?');
        $stmt->execute([$id]);
        $statuses = array_column($stmt->fetchAll(), 'status');

        if (in_array('DECLINED', $statuses, true)) {
            $newStatus = 'DECLINED';
        } elseif (count($statuses) > 0 && !in_array('PENDING', $statuses, true)) {
            $newStatus = 'CONFIRMED';
        } else {
            $newStatus = 'PENDING';
        }
        $db->prepare('UPDATE defenses SET status = ? WHERE id = ?')->execute([$newStatus, $id]);
    }

    /**
     * Reconcile a group's membership (student + adviser, only they may edit).
     * Body: { adviserId, panelistIds: [], studentIds: [] }. Changing the panel resets scheduling.
     */
    public function updateMembers(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $this->requireEditor($defense, $user);

        $body = Request::body();
        $adviserId = (int) ($body['adviserId'] ?? 0);
        $panelistIds = self::intList($body['panelistIds'] ?? []);
        $panelistIds = array_values(array_filter($panelistIds, fn($p) => $p !== $adviserId && $p > 0));
        $studentIds = self::intList($body['studentIds'] ?? []);
        // The creator always stays a student.
        $studentIds[] = (int) $defense['studentId'];
        $studentIds = array_values(array_unique(array_filter($studentIds, fn($s) => $s > 0)));

        if ($adviserId <= 0 || count($panelistIds) === 0) {
            Response::error('An adviser and at least one panelist are required');
        }

        $db = Database::conn();
        $oldMembers = array_map('intval', array_merge([$defense['adviserId']], $defense['panelistIds']));
        $newMembers = array_merge([$adviserId], $panelistIds);
        $panelChanged = array_values(array_unique($oldMembers)) != array_values(array_unique($newMembers));

        $db->beginTransaction();
        try {
            $db->prepare('UPDATE defenses SET adviser_id = ? WHERE id = ?')->execute([$adviserId, $id]);

            $db->prepare('DELETE FROM defense_panelists WHERE defense_id = ?')->execute([$id]);
            $insPanel = $db->prepare('INSERT INTO defense_panelists (defense_id, professor_id) VALUES (?, ?)');
            foreach ($panelistIds as $pid) {
                $insPanel->execute([$id, $pid]);
            }

            $db->prepare('DELETE FROM defense_students WHERE defense_id = ?')->execute([$id]);
            $insStudent = $db->prepare('INSERT INTO defense_students (defense_id, student_id) VALUES (?, ?)');
            foreach ($studentIds as $sid) {
                $insStudent->execute([$id, $sid]);
            }

            // Responses: keep retained members, drop removed, add new (all PENDING if panel changed).
            $db->prepare('DELETE FROM defense_responses WHERE defense_id = ?')->execute([$id]);
            $insResp = $db->prepare('INSERT INTO defense_responses (defense_id, member_id, status) VALUES (?, ?, ?)');
            foreach ($newMembers as $mid) {
                $insResp->execute([$id, $mid, 'PENDING']);
            }

            // Prune availability for anyone no longer in the group; seed newcomers from their default.
            $allParticipants = array_values(array_unique(array_merge($studentIds, $newMembers)));
            $keep = implode(',', array_fill(0, count($allParticipants), '?'));
            $db->prepare("DELETE FROM defense_availability WHERE defense_id = ? AND user_id NOT IN ($keep)")
                ->execute(array_merge([$id], $allParticipants));
            foreach ($allParticipants as $pid) {
                $has = $db->prepare('SELECT 1 FROM defense_availability WHERE defense_id = ? AND user_id = ? LIMIT 1');
                $has->execute([$id, $pid]);
                if (!$has->fetch()) {
                    $db->prepare(
                        'INSERT INTO defense_availability (defense_id, user_id, weekday, start_min, end_min)
                         SELECT ?, user_id, weekday, start_min, end_min FROM user_availability WHERE user_id = ?'
                    )->execute([$id, $pid]);
                }
            }

            if ($panelChanged) {
                $db->prepare("UPDATE defenses SET slot_weekday = NULL, slot_start = NULL, status = 'SCHEDULING' WHERE id = ?")
                    ->execute([$id]);
            }

            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            Response::error('Could not update group', 500);
        }

        Response::json(Presenter::defense($id));
    }

    /** Group chat messages, oldest first. Any participant may read. */
    public function messages(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $this->requireParticipant($defense, $user);
        Unread::markRead((int) $user['id'], 'GROUP', $id);
        Response::json($this->fetchMessages($id));
    }

    /** Post a message. Body: { body }. Any participant may post. */
    public function sendMessage(int $id): void
    {
        $user = Auth::requireUser();
        $defense = Presenter::defense($id);
        if (!$defense) {
            Response::error('Defense not found', 404);
        }
        $this->requireParticipant($defense, $user);

        $body = trim((string) (Request::body()['body'] ?? ''));
        if ($body === '') {
            Response::error('Message cannot be empty');
        }
        $body = mb_substr($body, 0, 2000);

        Database::conn()->prepare(
            'INSERT INTO defense_messages (defense_id, sender_id, body, created_at) VALUES (?, ?, ?, ?)'
        )->execute([$id, $user['id'], $body, (int) (microtime(true) * 1000)]);

        Response::json($this->fetchMessages($id));
    }

    /** Edit own group message. Body: { body } */
    public function editMessage(int $id, int $msgId): void
    {
        $user = Auth::requireUser();
        $body = trim((string) (Request::body()['body'] ?? ''));
        if ($body === '') {
            Response::error('Message cannot be empty');
        }
        $stmt = Database::conn()->prepare(
            'UPDATE defense_messages SET body = ?, edited = 1
             WHERE id = ? AND defense_id = ? AND sender_id = ?'
        );
        $stmt->execute([mb_substr($body, 0, 2000), $msgId, $id, $user['id']]);
        if ($stmt->rowCount() === 0) {
            Response::error('You can only edit your own messages', 403);
        }
        Response::json($this->fetchMessages($id));
    }

    /** Delete own group message. */
    public function deleteMessage(int $id, int $msgId): void
    {
        $user = Auth::requireUser();
        $stmt = Database::conn()->prepare(
            'DELETE FROM defense_messages WHERE id = ? AND defense_id = ? AND sender_id = ?'
        );
        $stmt->execute([$msgId, $id, $user['id']]);
        if ($stmt->rowCount() === 0) {
            Response::error('You can only delete your own messages', 403);
        }
        Response::json($this->fetchMessages($id));
    }

    private function fetchMessages(int $id): array
    {
        $stmt = Database::conn()->prepare(
            'SELECT m.id, m.sender_id, u.name AS sender_name, m.body, m.edited, m.created_at
             FROM defense_messages m JOIN users u ON u.id = m.sender_id
             WHERE m.defense_id = ? ORDER BY m.created_at ASC'
        );
        $stmt->execute([$id]);
        return array_map(fn($r) => [
            'id'         => (string) $r['id'],
            'senderId'   => (string) $r['sender_id'],
            'senderName' => $r['sender_name'],
            'body'       => $r['body'],
            'edited'     => (bool) $r['edited'],
            'createdAt'  => (int) $r['created_at'],
        ], $stmt->fetchAll());
    }

    private function requireParticipant(array $defense, array $user): void
    {
        $participants = array_merge(
            $defense['studentIds'],
            [$defense['adviserId']],
            $defense['panelistIds']
        );
        if (!in_array((string) $user['id'], $participants, true)) {
            Response::error('You do not have access to this defense', 403);
        }
    }

    /** The owning student or the adviser may edit membership. */
    private function requireEditor(array $defense, array $user): void
    {
        $me = (string) $user['id'];
        if ($me !== $defense['studentId'] && $me !== $defense['adviserId']) {
            Response::error('Only the student or adviser can edit this group', 403);
        }
    }

    private static function intList($value): array
    {
        return is_array($value) ? array_map('intval', $value) : [];
    }
}
