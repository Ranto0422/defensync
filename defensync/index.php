<?php
// DefenSync REST API — front controller.
// All routes are relative to this app's folder, e.g. http://10.0.2.2/defensync/auth/login

require_once __DIR__ . '/lib/Response.php';
require_once __DIR__ . '/controllers/AuthController.php';
require_once __DIR__ . '/controllers/UserController.php';
require_once __DIR__ . '/controllers/DefenseController.php';
require_once __DIR__ . '/controllers/DirectMessageController.php';

set_exception_handler(function (Throwable $e) {
    Response::error('Server error: ' . $e->getMessage(), 500);
});

$method = $_SERVER['REQUEST_METHOD'];

// Resolve the path relative to this app's base folder.
$base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'])), '/');
$uri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$path = $uri;
if ($base !== '' && strpos($uri, $base) === 0) {
    $path = substr($uri, strlen($base));
}
$path = trim($path, '/');
$segments = $path === '' ? [] : explode('/', $path);

$auth = new AuthController();
$users = new UserController();
$defenses = new DefenseController();
$dms = new DirectMessageController();

// --- Routing table ---------------------------------------------------------
if (count($segments) === 0) {
    Response::json(['service' => 'DefenSync API', 'status' => 'ok']);
}

// /auth/*
if ($segments[0] === 'auth') {
    $action = $segments[1] ?? '';
    if ($method === 'POST' && $action === 'register') $auth->register();
    if ($method === 'POST' && $action === 'login')    $auth->login();
    if ($method === 'GET'  && $action === 'me')       $auth->me();
    Response::error('Not found', 404);
}

// /professors
if ($segments[0] === 'professors' && count($segments) === 1) {
    if ($method === 'GET') $users->professors();
    Response::error('Method not allowed', 405);
}

// /students
if ($segments[0] === 'students' && count($segments) === 1) {
    if ($method === 'GET') $users->students();
    Response::error('Method not allowed', 405);
}

// /contacts  (people you can privately message)
if ($segments[0] === 'contacts' && count($segments) === 1) {
    if ($method === 'GET') $dms->contacts();
    Response::error('Method not allowed', 405);
}

// /inbox  (unified list of group chats + DMs, with unread counts)
if ($segments[0] === 'inbox' && count($segments) === 1) {
    if ($method === 'GET') $dms->inbox();
    Response::error('Method not allowed', 405);
}

// /messages/{userId}  (private 1-on-1 thread) and /messages/{userId}/{msgId}
if ($segments[0] === 'messages') {
    $otherId = (int) ($segments[1] ?? 0);
    if ($otherId <= 0) Response::error('Invalid user id', 400);
    $msgId = (int) ($segments[2] ?? 0);
    if ($msgId > 0) {
        if ($method === 'PUT')    $dms->edit($otherId, $msgId);
        if ($method === 'DELETE') $dms->delete($otherId, $msgId);
        Response::error('Method not allowed', 405);
    }
    if ($method === 'GET')  $dms->thread($otherId);
    if ($method === 'POST') $dms->send($otherId);
    Response::error('Method not allowed', 405);
}

// /availability  (current user's default weekly schedule)
if ($segments[0] === 'availability' && count($segments) === 1) {
    if ($method === 'GET') $users->getAvailability();
    if ($method === 'PUT') $users->updateAvailability();
    Response::error('Method not allowed', 405);
}

// /defenses ...
if ($segments[0] === 'defenses') {
    if (count($segments) === 1) {
        if ($method === 'GET')  $defenses->index();
        if ($method === 'POST') $defenses->create();
        Response::error('Method not allowed', 405);
    }

    $id = (int) ($segments[1] ?? 0);
    $sub = $segments[2] ?? '';
    $subId = (int) ($segments[3] ?? 0);

    if ($id <= 0) Response::error('Invalid defense id', 400);

    if ($sub === 'messages' && $subId > 0 && $method === 'PUT')    $defenses->editMessage($id, $subId);
    if ($sub === 'messages' && $subId > 0 && $method === 'DELETE') $defenses->deleteMessage($id, $subId);

    if ($sub === '' && $method === 'GET')                 $defenses->show($id);
    if ($sub === '' && $method === 'DELETE')              $defenses->delete($id);
    if ($sub === 'availability' && $method === 'PUT')     $defenses->updateAvailability($id);
    if ($sub === 'members' && $method === 'PUT')          $defenses->updateMembers($id);
    if ($sub === 'messages' && $method === 'GET')         $defenses->messages($id);
    if ($sub === 'messages' && $method === 'POST')        $defenses->sendMessage($id);
    if ($sub === 'slot' && $method === 'POST')            $defenses->selectSlot($id);
    if ($sub === 'respond' && $method === 'POST')         $defenses->respond($id);
    if ($sub === 'cancel' && $method === 'POST')          $defenses->cancel($id);

    Response::error('Not found', 404);
}

Response::error('Not found', 404);
