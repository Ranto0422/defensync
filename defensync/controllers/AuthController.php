<?php
require_once __DIR__ . '/../lib/Database.php';
require_once __DIR__ . '/../lib/Request.php';
require_once __DIR__ . '/../lib/Response.php';
require_once __DIR__ . '/../lib/Auth.php';
require_once __DIR__ . '/../lib/Presenter.php';

class AuthController
{
    public function register(): void
    {
        $body = Request::body();
        $name = trim($body['name'] ?? '');
        $email = strtolower(trim($body['email'] ?? ''));
        $password = (string) ($body['password'] ?? '');
        $role = strtoupper(trim($body['role'] ?? 'STUDENT'));
        $studentNumber = trim($body['studentNumber'] ?? '');

        if ($name === '' || $email === '') {
            Response::error('Name and email are required');
        }
        if (strlen($password) < 6) {
            Response::error('Password must be at least 6 characters');
        }
        if (!in_array($role, ['STUDENT', 'PROFESSOR'], true)) {
            $role = 'STUDENT';
        }
        // Students are identified by a student number; professors don't have one.
        if ($role === 'STUDENT' && $studentNumber === '') {
            Response::error('Student number is required');
        }
        $studentNumber = $role === 'STUDENT' ? $studentNumber : null;

        $db = Database::conn();
        $exists = $db->prepare('SELECT id FROM users WHERE email = ?');
        $exists->execute([$email]);
        if ($exists->fetch()) {
            Response::error('An account with that email already exists', 409);
        }
        if ($studentNumber !== null) {
            $numTaken = $db->prepare('SELECT id FROM users WHERE student_number = ?');
            $numTaken->execute([$studentNumber]);
            if ($numTaken->fetch()) {
                Response::error('That student number is already registered', 409);
            }
        }

        $token = Auth::newToken();
        $stmt = $db->prepare(
            'INSERT INTO users (name, email, password_hash, role, student_number, api_token, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([
            $name,
            $email,
            password_hash($password, PASSWORD_DEFAULT),
            $role,
            $studentNumber,
            $token,
            (int) (microtime(true) * 1000),
        ]);
        $id = (int) $db->lastInsertId();

        $user = $this->findUser($id);
        Response::json(['token' => $token, 'user' => Presenter::user($user)]);
    }

    public function login(): void
    {
        $body = Request::body();
        $email = strtolower(trim($body['email'] ?? ''));
        $password = (string) ($body['password'] ?? '');

        $db = Database::conn();
        $stmt = $db->prepare('SELECT * FROM users WHERE email = ?');
        $stmt->execute([$email]);
        $user = $stmt->fetch();

        if (!$user || !password_verify($password, $user['password_hash'])) {
            Response::error('Invalid email or password', 401);
        }

        $token = Auth::newToken();
        $db->prepare('UPDATE users SET api_token = ? WHERE id = ?')
            ->execute([$token, $user['id']]);

        Response::json(['token' => $token, 'user' => Presenter::user($user)]);
    }

    public function me(): void
    {
        $user = Auth::requireUser();
        Response::json(['user' => Presenter::user($user)]);
    }

    private function findUser(int $id): array
    {
        $stmt = Database::conn()->prepare('SELECT * FROM users WHERE id = ?');
        $stmt->execute([$id]);
        return $stmt->fetch();
    }
}
