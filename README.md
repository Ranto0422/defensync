# DefenSync

An Android app that finds a thesis defense time that works for a student, their adviser and every panelist. Everyone marks when they are free, the app computes the windows the whole panel shares, and the student locks one in.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor_client-087CFA?logo=ktor&logoColor=white)
![PHP](https://img.shields.io/badge/PHP_8-777BB4?logo=php&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)

| | |
|---|---|
| **Problem** | Scheduling a defense means aligning 3 to 5 busy people by hand over chat |
| **Stack** | Kotlin, Jetpack Compose (Material 3), MVVM with StateFlow, Ktor client / PHP 8 REST API, MySQL |
| **Size** | ~7k lines of Kotlin, plus a PHP API with 4 controllers |
| **Highlights** | Exact-minute interval overlap engine with unit tests, token auth, group and direct chat, configurable server address |

## Features

- **Two roles.** Students and professors sign up separately. A student creates a defense group and assigns one adviser, the panelists and any co-students (searchable by student number).
- **Weekly availability.** Mark the hours you are usually free (Mon to Sat, 7:00 AM to 9:00 PM) once, then adjust it per defense group.
- **Overlap timeline.** A vertical timeline shows each member's free time and the windows everyone shares, to the exact minute (free until 4:35 PM shows as 4:35, not rounded).
- **Suggested times.** The app lists every 3-hour block that fits everyone. The student can pick one or type an exact custom time, which is checked against the shared windows.
- **Confirm and respond.** The student proposes a time, each member accepts or declines, and the defense is confirmed once everyone accepts.
- **Messaging.** Group chat per defense and one-on-one direct messages, with unread counts.
- **Multiple saved accounts** on one device, and a setting to point the app at any server IP.

## How the scheduling works

The core logic is in [`SchedulingEngine.kt`](app/src/main/java/com/example/defensync/domain/scheduling/SchedulingEngine.kt) and is pure Kotlin with no Android dependencies, so it is unit tested directly:

1. `mergeRanges` unions each person's overlapping or touching free ranges.
2. `overlapRanges` intersects the merged ranges of every member to get the windows the whole panel shares.
3. `findAlignedSlots` walks those windows and returns every start time where a full 3-hour defense fits.
4. `fitsBlock` validates a custom time the student types in.

Tests: [`OverlapRangesTest.kt`](app/src/test/java/com/example/defensync/OverlapRangesTest.kt), [`FitsBlockTest.kt`](app/src/test/java/com/example/defensync/FitsBlockTest.kt).

## Project structure

```
defensync/
├── app/src/main/java/com/example/defensync/
│   ├── core/          HTTP client, token store, server URL setting, service locator
│   ├── data/          DTOs, Ktor API classes, repositories
│   ├── domain/        models and the scheduling engine
│   └── ui/            Compose screens: auth, schedule, defense, chat, shared components
├── app/src/test/      unit tests for the scheduling engine
└── backend/           PHP REST API (copy into XAMPP's htdocs)
    ├── index.php      front controller and router
    ├── controllers/   Auth, User, Defense, DirectMessage
    ├── lib/           Database, Auth, Request, Response helpers
    ├── config.php     MySQL connection settings
    └── schema.sql     database schema
```

## Running it

You need Android Studio, an Android emulator (or a phone on the same Wi-Fi), and XAMPP with Apache and MySQL.

### 1. Backend

1. Copy the `backend/` folder into XAMPP's web root and rename it `defensync`:
   - macOS: `/Applications/XAMPP/htdocs/defensync/`
   - Windows: `C:\xampp\htdocs\defensync\`
2. Start **Apache** and **MySQL** in XAMPP.
3. Open http://localhost/phpmyadmin, choose **Import**, and import `defensync/schema.sql`.
4. If your MySQL `root` user has a password, set it in `config.php`.
5. Open http://localhost/defensync/. You should see `{"service":"DefenSync API","status":"ok"}`.

### 2. App

1. Open the project in Android Studio and let Gradle sync.
2. Run it on an emulator. The default server address is `http://10.0.2.2/defensync/`, which is how the emulator reaches your computer.
3. On a physical phone, change the server address in the app to your computer's LAN IP, for example `http://192.168.1.20/defensync/`.

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## API reference

Base URL: `http://localhost/defensync/`. Every request after login sends `Authorization: Bearer <token>`. Passwords are stored with PHP `password_hash`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Create account, returns `{token, user}` |
| POST | `/auth/login` | Log in, returns `{token, user}` |
| GET | `/auth/me` | Current user |
| GET | `/professors` | Professors a student can invite |
| GET | `/students` | Search students by name or number |
| GET, PUT | `/availability` | Get or replace my default weekly schedule |
| GET, POST | `/defenses` | List my defenses, or create a group |
| GET | `/defenses/{id}` | One defense |
| PUT | `/defenses/{id}/members` | Update adviser, panelists, co-students |
| PUT | `/defenses/{id}/availability` | My availability for this group |
| POST | `/defenses/{id}/slot` | Student proposes a time |
| POST | `/defenses/{id}/respond` | Member accepts or declines |
| POST | `/defenses/{id}/cancel` | Student cancels |
| GET, POST | `/defenses/{id}/messages` | Group chat |
| GET, POST | `/messages/{userId}` | Direct chat |

REST has no push, so the defense list polls about every 4 seconds while open and refreshes right after any action.
