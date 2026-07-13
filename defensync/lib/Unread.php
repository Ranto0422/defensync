<?php
require_once __DIR__ . '/Database.php';

// Unread-count helpers backed by the chat_reads cursor table.
class Unread
{
    public static function lastRead(int $userId, string $ctype, int $ckey): int
    {
        $stmt = Database::conn()->prepare(
            'SELECT last_read_at FROM chat_reads WHERE user_id = ? AND ctype = ? AND ckey = ?'
        );
        $stmt->execute([$userId, $ctype, $ckey]);
        $row = $stmt->fetch();
        return $row ? (int) $row['last_read_at'] : 0;
    }

    /** Unread group-chat messages in a defense (messages from others after the read cursor). */
    public static function group(int $userId, int $defenseId): int
    {
        $since = self::lastRead($userId, 'GROUP', $defenseId);
        $stmt = Database::conn()->prepare(
            'SELECT COUNT(*) AS n FROM defense_messages
             WHERE defense_id = ? AND sender_id <> ? AND created_at > ?'
        );
        $stmt->execute([$defenseId, $userId, $since]);
        return (int) $stmt->fetch()['n'];
    }

    /** Unread DMs from [otherId] to [userId] after the read cursor. */
    public static function dm(int $userId, int $otherId): int
    {
        $since = self::lastRead($userId, 'DM', $otherId);
        $stmt = Database::conn()->prepare(
            'SELECT COUNT(*) AS n FROM direct_messages
             WHERE recipient_id = ? AND sender_id = ? AND created_at > ?'
        );
        $stmt->execute([$userId, $otherId, $since]);
        return (int) $stmt->fetch()['n'];
    }

    public static function markRead(int $userId, string $ctype, int $ckey): void
    {
        $now = (int) (microtime(true) * 1000);
        Database::conn()->prepare(
            'INSERT INTO chat_reads (user_id, ctype, ckey, last_read_at) VALUES (?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE last_read_at = VALUES(last_read_at)'
        )->execute([$userId, $ctype, $ckey, $now]);
    }
}
