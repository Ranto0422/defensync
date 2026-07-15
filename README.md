# DefenSync

An Android app (Kotlin + Jetpack Compose) that aligns thesis/capstone **defense schedules** between a
student, their adviser, and panelists. Everyone marks when they're free; the app finds the 3-hour
windows that work for the whole panel and lets the student lock one in. The backend is a **PHP + MySQL
REST API** served by **XAMPP**, and the app talks to it with the **Ktor** HTTP client. No Firebase, no
Cloud Functions.

## Features

- **Role-based accounts** — sign up as a **Student** or **Professor**; the student assigns adviser vs.
  panelist per defense. Students register with a **student number** (used to search + add group members).
- **Weekly availability** — mark the hours you're usually free (Mon–Sat, 7:00 AM–9:00 PM) as a reusable
  default, then fine-tune it per defense group.
- **Overlap timeline** — a vertical, exact-minute timeline shows when each panel member is free and the
  windows the whole panel shares (e.g. free only until 4:35 PM renders truthfully, not snapped).
- **Aligned times** — the app suggests every 3 consecutive free hours that works for everyone; the
  student can pick a suggestion or set an **exact custom time** (e.g. 10:33 AM), validated to fit a
  window everyone shares.
- **Confirm & respond** — the student confirms a time, each member Accepts/Declines, and all-accept ⇒
  **Confirmed**. A group schedule screen shows the confirmed time against everyone's day.
- **Messaging** — group and direct chat between participants.

## How it works

1. Sign up as **Student** or **Professor**.
2. On **My Schedule**, mark the hours you're usually free — a reusable default template.
3. A **student** creates a defense **group** (title, one adviser, panelists, optional co-students).
4. Inside the group, each participant tweaks availability *for that group* (seeded from their default;
   untouched copies keep following default-schedule edits).
5. The app lists every **3 consecutive free hours** that works for everyone.
6. The student picks (or types) a window → each member **Accepts/Declines** → all accept ⇒ **Confirmed**.

## Tech stack

| Layer | Tech |
|-------|------|
| UI | Jetpack Compose (Material 3), Navigation Compose |
| Architecture | MVVM, `StateFlow` + `collectAsStateWithLifecycle` |
| Networking | Ktor client, token (`Authorization: Bearer …`) auth |
| Backend | PHP 8 (PDO), MySQL, served by XAMPP (Apache) |

## Project structure

```
app/                                  Android app (Kotlin + Compose)
  src/main/java/com/example/defensync/
    core/        network client, token store, service locator
    data/        DTOs, Ktor remote APIs, repositories
    domain/      models + scheduling engine (overlap / aligned windows)
    ui/          Compose screens (auth, schedule, defense, chat, components)
  src/test/                           unit tests (scheduling logic)
```

The **backend** PHP files live in the XAMPP web root at `/Applications/XAMPP/htdocs/defensync/`:
a front controller (`index.php`) routing to `controllers/`, with `lib/` helpers and `schema.sql`.

## Requirements

- **Android Studio** (use its bundled JBR as `JAVA_HOME`); the app builds against **compileSdk 37**.
- An **Android emulator** (or a physical device on the same network).
- **XAMPP** with **Apache** + **MySQL** (PHP 8+).

## Setup

### 1. Backend (XAMPP)

The PHP files are at `/Applications/XAMPP/htdocs/defensync/`.

1. Open **XAMPP** and **Start** both **Apache** and **MySQL**.
2. Create the database — open http://localhost/phpmyadmin → **Import** →
   choose `/Applications/XAMPP/htdocs/defensync/schema.sql` → **Go**.
   (Or run: `/Applications/XAMPP/bin/mysql -u root < /Applications/XAMPP/htdocs/defensync/schema.sql`)
3. If your MySQL `root` user has a password, set it in
   `/Applications/XAMPP/htdocs/defensync/config.php` (defaults to `root` / no password).
4. Verify — open http://localhost/defensync/ → you should see
   `{"service":"DefenSync API","status":"ok"}`.

### 2. App

1. Open the project in **Android Studio** and let Gradle sync.
2. The base URL is `http://10.0.2.2/defensync/` (in `core/network/ApiClient.kt`) — `10.0.2.2` is how the
   **Android emulator** reaches XAMPP on your Mac. Build & run on an emulator.
3. **Physical phone?** Put the phone on the same Wi-Fi and change `ApiConfig.BASE_URL` to your Mac's LAN
   IP, e.g. `http://192.168.1.20/defensync/`.

Command line (uses Android Studio's bundled JDK):

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

## API reference (base `http://localhost/defensync/`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/register` | Create account → `{token, user}` |
| POST | `/auth/login` | Log in → `{token, user}` |
| GET | `/auth/me` | Current user |
| GET | `/professors` | Professors a student can invite |
| GET | `/students` | Students (search by name / number) |
| GET/PUT | `/availability` | Get/replace my default weekly schedule |
| GET | `/defenses` | Defenses I'm part of |
| POST | `/defenses` | Create a group (student) |
| GET | `/defenses/{id}` | One defense |
| PUT | `/defenses/{id}/members` | Update adviser/panelists/co-students |
| PUT | `/defenses/{id}/availability` | Replace my availability for that group |
| POST | `/defenses/{id}/slot` | Student confirms a time |
| POST | `/defenses/{id}/respond` | Member accepts/declines |
| POST | `/defenses/{id}/cancel` | Student cancels |
| GET/POST | `/defenses/{id}/messages` | Group chat thread |
| GET/POST | `/messages/{userId}` | Direct 1-on-1 chat thread |

Auth: register/login returns a token stored on the device; every request sends it as
`Authorization: Bearer <token>`. Passwords are hashed with PHP `password_hash`. Since REST has no push,
the defense list polls every ~4s while open and refreshes immediately after any action.
