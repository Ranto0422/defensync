<?php
require_once __DIR__ . '/../lib/Database.php';
require_once __DIR__ . '/../lib/Request.php';
require_once __DIR__ . '/../lib/Response.php';
require_once __DIR__ . '/../lib/Auth.php';
require_once __DIR__ . '/../lib/Unread.php';

// Private 1-on-1 messaging + the unified inbox (group chats + DMs).
class DirectMessageController
{
    /** People the current user can message, with a preview of the latest DM if any. */
    public function contacts(): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        $db = Database::conn();

        $contactIds = $this->myContactIds($me);
        if (!$contactIds) {
            Response::json([]);
        }

        $in2 = implode(',', array_fill(0, count($contactIds), '?'));
        $ustmt = $db->prepare("SELECT id, name, role FROM users WHERE id IN ($in2) ORDER BY name");
        $ustmt->execute($contactIds);

        $contacts = [];
        foreach ($ustmt->fetchAll() as $u) {
            $uid = (int) $u['id'];
            $last = $this->lastDm($me, $uid);
            $contacts[] = [
                'id'          => (string) $uid,
                'name'        => $u['name'],
                'role'        => $u['role'],
                'lastMessage' => $last ? $last['body'] : null,
                'lastAt'      => $last ? (int) $last['created_at'] : 0,
            ];
        }
        usort($contacts, fn($a, $b) => $b['lastAt'] <=> $a['lastAt']);
        Response::json($contacts);
    }

    /** Unified inbox: every group chat I'm in + every DM contact, newest activity first. */
    public function inbox(): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        $db = Database::conn();
        $items = [];

        foreach ($this->myDefenseIds($me) as $did) {
            $t = $db->prepare('SELECT title FROM defenses WHERE id = ?');
            $t->execute([$did]);
            $title = $t->fetch()['title'] ?? 'Defense';

            $lm = $db->prepare(
                'SELECT body, created_at FROM defense_messages WHERE defense_id = ? ORDER BY created_at DESC LIMIT 1'
            );
            $lm->execute([$did]);
            $last = $lm->fetch();

            $items[] = [
                'type'     => 'group',
                'key'      => (string) $did,
                'title'    => $title !== '' ? $title : 'Defense group',
                'subtitle' => $last ? $last['body'] : 'Group chat',
                'lastAt'   => $last ? (int) $last['created_at'] : 0,
                'unread'   => Unread::group($me, $did),
            ];
        }

        // Only DMs that actually have messages appear here. New DMs are started from the
        // "Message" button on a defense's panel, so the inbox stays an active-conversation list.
        $contactIds = $this->myContactIds($me);
        if ($contactIds) {
            $in = implode(',', array_fill(0, count($contactIds), '?'));
            $ustmt = $db->prepare("SELECT id, name, role FROM users WHERE id IN ($in)");
            $ustmt->execute($contactIds);
            foreach ($ustmt->fetchAll() as $u) {
                $uid = (int) $u['id'];
                $last = $this->lastDm($me, $uid);
                if (!$last) {
                    continue;
                }
                $items[] = [
                    'type'     => 'dm',
                    'key'      => (string) $uid,
                    'title'    => $u['name'],
                    'subtitle' => $last['body'],
                    'lastAt'   => (int) $last['created_at'],
                    'unread'   => Unread::dm($me, $uid),
                ];
            }
        }

        usort($items, fn($a, $b) => $b['lastAt'] <=> $a['lastAt']);
        Response::json($items);
    }

    private function myDefenseIds(int $me): array
    {
        $stmt = Database::conn()->prepare(
            'SELECT DISTINCT d.id FROM defenses d
             WHERE d.adviser_id = ?
                OR EXISTS (SELECT 1 FROM defense_students ds WHERE ds.defense_id = d.id AND ds.student_id = ?)
                OR EXISTS (SELECT 1 FROM defense_panelists p WHERE p.defense_id = d.id AND p.professor_id = ?)'
        );
        $stmt->execute([$me, $me, $me]);
        return array_map('intval', array_column($stmt->fetchAll(), 'id'));
    }

    private function myContactIds(int $me): array
    {
        $db = Database::conn();
        $defenseIds = $this->myDefenseIds($me);
        $ids = [];
        if ($defenseIds) {
            $in = implode(',', array_fill(0, count($defenseIds), '?'));
            foreach ([
                "SELECT adviser_id AS uid FROM defenses WHERE id IN ($in)",
                "SELECT student_id AS uid FROM defense_students WHERE defense_id IN ($in)",
                "SELECT professor_id AS uid FROM defense_panelists WHERE defense_id IN ($in)",
            ] as $sql) {
                $s = $db->prepare($sql);
                $s->execute($defenseIds);
                foreach ($s->fetchAll() as $r) {
                    $ids[] = (int) $r['uid'];
                }
            }
        }
        $dm = $db->prepare('SELECT sender_id, recipient_id FROM direct_messages WHERE sender_id = ? OR recipient_id = ?');
        $dm->execute([$me, $me]);
        foreach ($dm->fetchAll() as $r) {
            $ids[] = (int) $r['sender_id'];
            $ids[] = (int) $r['recipient_id'];
        }
        return array_values(array_unique(array_filter($ids, fn($id) => $id !== $me)));
    }

    private function lastDm(int $me, int $other): ?array
    {
        $stmt = Database::conn()->prepare(
            'SELECT body, created_at FROM direct_messages
             WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)
             ORDER BY created_at DESC LIMIT 1'
        );
        $stmt->execute([$me, $other, $other, $me]);
        return $stmt->fetch() ?: null;
    }

    /** The DM thread between me and [otherId], oldest first. */
    public function thread(int $otherId): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        if ($otherId === $me) {
            Response::error('Cannot message yourself', 400);
        }
        Unread::markRead($me, 'DM', $otherId);
        Response::json($this->fetchThread($me, $otherId));
    }

    /** Send a DM to [otherId]. Body: { body }. */
    public function send(int $otherId): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        if ($otherId === $me) {
            Response::error('Cannot message yourself', 400);
        }

        $exists = Database::conn()->prepare('SELECT 1 FROM users WHERE id = ?');
        $exists->execute([$otherId]);
        if (!$exists->fetch()) {
            Response::error('User not found', 404);
        }

        $body = trim((string) (Request::body()['body'] ?? ''));
        if ($body === '') {
            Response::error('Message cannot be empty');
        }
        $body = mb_substr($body, 0, 2000);

        Database::conn()->prepare(
            'INSERT INTO direct_messages (sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?)'
        )->execute([$me, $otherId, $body, (int) (microtime(true) * 1000)]);

        Response::json($this->fetchThread($me, $otherId));
    }

    /** Edit own DM. Body: { body } */
    public function edit(int $otherId, int $msgId): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        $body = trim((string) (Request::body()['body'] ?? ''));
        if ($body === '') {
            Response::error('Message cannot be empty');
        }
        $stmt = Database::conn()->prepare(
            'UPDATE direct_messages SET body = ?, edited = 1
             WHERE id = ? AND sender_id = ? AND recipient_id = ?'
        );
        $stmt->execute([mb_substr($body, 0, 2000), $msgId, $me, $otherId]);
        if ($stmt->rowCount() === 0) {
            Response::error('You can only edit your own messages', 403);
        }
        Response::json($this->fetchThread($me, $otherId));
    }

    /** Delete own DM. */
    public function delete(int $otherId, int $msgId): void
    {
        $user = Auth::requireUser();
        $me = (int) $user['id'];
        $stmt = Database::conn()->prepare(
            'DELETE FROM direct_messages WHERE id = ? AND sender_id = ? AND recipient_id = ?'
        );
        $stmt->execute([$msgId, $me, $otherId]);
        if ($stmt->rowCount() === 0) {
            Response::error('You can only delete your own messages', 403);
        }
        Response::json($this->fetchThread($me, $otherId));
    }

    private function fetchThread(int $me, int $other): array
    {
        $stmt = Database::conn()->prepare(
            'SELECT id, sender_id, body, edited, created_at FROM direct_messages
             WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)
             ORDER BY created_at ASC'
        );
        $stmt->execute([$me, $other, $other, $me]);
        return array_map(fn($r) => [
            'id'        => (string) $r['id'],
            'senderId'  => (string) $r['sender_id'],
            'body'      => $r['body'],
            'edited'    => (bool) $r['edited'],
            'createdAt' => (int) $r['created_at'],
        ], $stmt->fetchAll());
    }
}
