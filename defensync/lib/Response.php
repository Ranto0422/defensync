<?php
// JSON response helpers. Every handler ends by calling one of these.
class Response
{
    public static function json($data, int $code = 200): void
    {
        http_response_code($code);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode($data);
        exit;
    }

    public static function error(string $message, int $code = 400): void
    {
        self::json(['error' => $message], $code);
    }
}
