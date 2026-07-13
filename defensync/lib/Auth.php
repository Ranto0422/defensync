<?php
require_once __DIR__ . '/Database.php';
require_once __DIR__ . '/Request.php';
require_once __DIR__ . '/Response.php';

// Token-based authentication. A token is issued at register/login and stored on the user row.
class Auth
{
    /** Returns the authenticated user row, or sends 401 and exits. */
    public static function requireUser(): array
    {
        $token = Request::bearerToken();
        if (!$token) {
            Response::error('Missing authorization token', 401);
        }
        $stmt = Database::conn()->prepare('SELECT * FROM users WHERE api_token = ?');
        $stmt->execute([$token]);
        $user = $stmt->fetch();
        if (!$user) {
            Response::error('Invalid or expired token', 401);
        }
        return $user;
    }

    public static function newToken(): string
    {
        return bin2hex(random_bytes(32));
    }
}
