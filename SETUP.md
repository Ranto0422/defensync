# DefenSync — setup

An Android app (Kotlin + Jetpack Compose) that aligns defense schedules between a student,
their adviser, and panelists. The backend is a **PHP + MySQL REST API** served by **XAMPP**,
and the app talks to it with the **Ktor** HTTP client. No Firebase, no Cloud Functions.

## Architecture
- **Backend:** `/Applications/XAMPP/htdocs/defensync/` — plain PHP (PDO/MySQL), a small front
  controller (`index.php`) routing to `controllers/`, with `lib/` helpers.
- **App:** layered — `domain/` (models + scheduling), `data/` (dto, remote Ktor APIs,
  repositories), `core/` (network client, token store, service locator), `ui/` (Compose screens).
- **Auth:** register/login returns a token stored on the device; every request sends it as
  `Authorization: Bearer <token>`. Passwords are hashed with PHP `password_hash`.
- **Live updates:** REST has no push, so the defense list polls every ~4s while open, and
  refreshes immediately after any action.

## How it works
1. Sign up as **Student** or **Professor** (only two roles; the student assigns adviser vs panelist per defense).
2. On **My Schedule**, mark the hours you're usually free (Mon–Sun, 7 AM–7 PM) — a reusable default template.
3. A **student** creates a defense **group** (title, one adviser, panelists).
4. Inside the group, each participant tweaks availability *for that group* (seeded from their default).
5. The app lists every **3 consecutive free hours** that works for everyone.
6. The student picks a window → each member **Accepts/Declines** → all accept ⇒ **Confirmed**.

## One-time setup

### 1. Backend (XAMPP)
The PHP files are already at `/Applications/XAMPP/htdocs/defensync/`.
1. Open **XAMPP** and **Start** both **Apache** and **MySQL**.
2. Create the database: open http://localhost/phpmyadmin → **Import** →
   choose `/Applications/XAMPP/htdocs/defensync/schema.sql` → **Go**.
   (Or run: `/Applications/XAMPP/bin/mysql -u root < /Applications/XAMPP/htdocs/defensync/schema.sql`)
3. If your MySQL `root` user has a password, set it in
   `/Applications/XAMPP/htdocs/defensync/config.php`.
4. Verify: open http://localhost/defensync/ → you should see
   `{"service":"DefenSync API","status":"ok"}`.

### 2. App
- The base URL is `http://10.0.2.2/defensync/` (defined in
  `core/network/ApiClient.kt`) — `10.0.2.2` is how the **Android emulator** reaches XAMPP on
  this Mac. Build & run on an emulator from Android Studio (bundled JDK works).
- **Physical phone?** Put the phone on the same Wi-Fi and change `ApiConfig.BASE_URL` to your
  Mac's LAN IP, e.g. `http://192.168.1.20/defensync/`.

## API reference (base `http://localhost/defensync/`)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/register` | Create account → `{token, user}` |
| POST | `/auth/login` | Log in → `{token, user}` |
| GET | `/auth/me` | Current user |
| GET | `/professors` | Professors a student can invite |
| GET/PUT | `/availability` | Get/replace my default weekly schedule |
| GET | `/defenses` | Defenses I'm part of |
| POST | `/defenses` | Create a group (student) |
| GET | `/defenses/{id}` | One defense |
| PUT | `/defenses/{id}/availability` | Replace my availability for that group |
| POST | `/defenses/{id}/slot` | Student confirms a time |
| POST | `/defenses/{id}/respond` | Member accepts/declines |
| POST | `/defenses/{id}/cancel` | Student cancels |
