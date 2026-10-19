<?php
// Helpers for reading the incoming request.
class Request
{
    /** Parsed JSON body as an associative array (empty array if none). */
    public static function body(): array
    {
        $raw = file_get_contents('php://input');
        if ($raw === '' || $raw === false) {
            return [];
        }
        $data = json_decode($raw, true);
        return is_array($data) ? $data : [];
    }

    /** The Bearer token from the Authorization header, or null. */
    public static function bearerToken(): ?string
    {
        $auth = '';
        if (function_exists('getallheaders')) {
            foreach (getallheaders() as $key => $value) {
                if (strcasecmp($key, 'Authorization') === 0) {
                    $auth = $value;
                    break;
                }
            }
        }
        if ($auth === '') {
            $auth = $_SERVER['HTTP_AUTHORIZATION']
                ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
                ?? '';
        }
        if (preg_match('/Bearer\s+(.+)/i', $auth, $m)) {
            return trim($m[1]);
        }
        return null;
    }
}
