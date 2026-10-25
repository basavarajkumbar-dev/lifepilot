# LifePilot — Personal Life OS

A local-first Android app for tasks, routines, habits and reminders that **keep reminding you until a task is actually completed**. No account, no cloud, no analytics, no internet permission.

Kotlin · Jetpack Compose (Material 3, custom design system) · MVVM · Room · Coroutines/Flow · AlarmManager + WorkManager · DataStore · Navigation Compose · Gradle Kotlin DSL

---

## Get the APK

Every push builds a debug APK and a release APK (signed with the debug key, for evaluation) on GitHub Actions:

1. Open the repository's **Actions** tab → latest **Android CI** run.
2. Download the **LifePilot-debug-apk** artifact (zip containing `app-debug.apk`).
3. On your phone, allow installs from your browser/file manager and open the APK.

Build output paths: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

## Build locally

Requirements: JDK 17+, Android SDK with platform 35 (Android Studio Ladybug or newer installs both).

```bash
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew assembleDebug            # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest  # instrumented + UI tests (emulator/device attached)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

Toolchain (verified together in CI): AGP 8.7.3, Gradle 8.14.3, Kotlin 2.1.21, KSP 2.1.21-2.0.1, Compose BOM 2025.05.00, Room 2.7.1, WorkManager 2.10.1, Navigation 2.8.9, DataStore 1.1.4. minSdk 26, target/compile SDK 35.

---

## Features

- **Home dashboard** — greeting, date, progress ring, remaining/overdue/done (each tappable), next reminder, quick actions (plan my day, review pending, end-of-day review, calendar), All/Pending/Completed/Overdue filters, high-priority tasks, today's habits, quick-add.
- **Tasks** — full CRUD, title/notes/category/priority (Low–Critical)/due date & time/start/duration/tags, search, filters, sorting, duplicate, long-press multi-select with confirmed bulk delete, custom categories.
- **Remind me until completed** — first reminder at due time or an offset before it; repeat every 5/10/15/30/60 min or custom; optional max per day; continue after due; overdue reminders on later days; optional hard stop; global and per-task quiet hours; master switch and per-task switch; actionable notifications (Complete / Snooze / Open).
- **Recurring tasks** — daily, weekdays, weekly on chosen days, every N days/weeks/months/years, specific days of month (incl. "last day"; 31st clamps to short months), yearly, end date; configurable handling of missed occurrences.
- **Habits** — yes/no or measurable (partial progress), daily / specific days / N times per week, preferred time + reminder, current & best streaks, week strip, month calendar (tap past days to correct), 30-day consistency. Neutral, non-shaming copy.
- **Calendar** — day / week / month views, per-day indicators (pending, overdue, done, upcoming recurring), agenda, create a task on any date, overlap warnings in the editor. Nothing is moved silently.
- **Insights** — today/week completion rates, completed vs created (7 days), 14-day trend, most productive weekdays, category breakdown, habit consistency, overdue/missed/rescheduled/snooze counts — all computed from real records; graceful empty state.
- **Quick capture** — conservative natural-language parsing ("Call mom tomorrow at 6pm !high"); what was understood is always shown; ambiguous phrases are left alone.
- **Daily planning & end-of-day review** — set an intention, star up to 3 priorities, decide per overdue item; review completed/open/reminded-but-not-done/habits. Bulk "move to tomorrow" only after confirmation.
- **Settings** — theme (dark default, light, system), reminder defaults, quiet hours, sound/vibration, week start, 12/24h, date format, habit reminders, categories, **Reminder reliability** screen, JSON backup/restore, CSV export, clear data, privacy & version.

## Architecture

```
app/src/main/java/com/lifepilot/app/
├── domain/            Pure Kotlin — no Android imports (unit-tested on the JVM)
│   ├── model/         Task, Occurrence, ReminderConfig, RecurrenceRule, Habit, settings…
│   ├── reminder/      ReminderPolicy: computes the next reminder from persisted state
│   ├── recurrence/    RecurrenceEngine: date rules for recurring tasks
│   ├── service/       TaskService / HabitService: every state change + alarm sync
│   ├── repository/    Repository interfaces (persistence boundary)
│   ├── scheduling/    AlarmScheduler / ReminderNotifier interfaces, AlarmKey identities
│   ├── analytics/     InsightsCalculator
│   ├── habit/         HabitStats (streaks, rates, schedules)
│   ├── quickadd/      QuickAddParser
│   ├── backup/        Backup DTOs, BackupValidator, CsvExporter
│   └── time/          TimeProvider (injectable clock)
├── data/              Room entities/DAOs/migrations, repositories, DataStore, backup JSON
├── scheduling/        AlarmManager scheduler, alarm/action/system receivers, WorkManager safety net
├── notifications/     Channels and actionable notifications
├── reminder/          ReliabilityChecker (permissions, exact alarms, battery, DND, OEM)
├── ui/                Compose design system, navigation, screens + ViewModels
├── AppContainer.kt    Manual dependency injection
└── LifePilotApp.kt    Application: channels, periodic worker, reconcile on start
```

**Data model.** A `Task` is a definition (one-off, or the template of a series) with a 1:1 `reminder_configs` row and an optional `recurrence_rules` row. Each scheduled instance is an `Occurrence` — the **single source of truth** for completion and reminder state. A unique index on `(taskId, seriesDate)` makes duplicate occurrences impossible. `reminder_log` keeps fired/snoozed/completed/rescheduled history. Habits and habit logs are separate tables (unique per habit per day). Instants are stored in UTC millis; due dates/times are stored as local wall-clock values so a task due at 20:00 stays at 20:00 when you change timezone.

**How "remind until completed" works.**
1. `ReminderPolicy.next()` is a pure function of (config, occurrence state, quiet hours, master switch, now, zone). It never depends on in-memory timers, so it gives the same answer after a reboot, process death, timezone change or duplicate job.
2. `TaskService` persists the result (`nextReminderAt`) and asks the `AlarmScheduler` for exactly one alarm per occurrence. Each alarm uses a PendingIntent whose identity is the URI `lifepilot://alarm/task/{id}`, so re-scheduling **replaces** instead of duplicating, and cancelling targets exactly that alarm.
3. When an alarm fires, the receiver re-computes the decision from the database. A stale alarm (task completed, rescheduled, quiet hours changed, clock moved) is re-planned rather than shown. Otherwise the notification is posted, the fire is recorded, and the next alarm is scheduled.
4. **Complete** uses a conditional SQL update (`WHERE status='PENDING'`) inside a process-wide mutex, so a notification tap and a UI tap at the same time cannot produce inconsistent state; future alarms and the notification are cancelled. **Snooze** only sets `snoozedUntil`. **Dismiss** has no handler at all — the next alarm stays scheduled.
5. Alarms are rebuilt (`reconcileAll`, idempotent) on app start, `BOOT_COMPLETED`, app update, `TIME_SET`, `TIMEZONE_CHANGED`, exact-alarm permission change, and by an 8-hourly WorkManager safety net that also generates the next recurring occurrences.

## Android permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Show reminders (runtime permission on Android 13+; requested from onboarding / Reliability screen, never automatically). |
| `SCHEDULE_EXACT_ALARM` | Deliver reminders at the exact minute. On Android 12+ the user grants "Alarms & reminders"; without it the app falls back to inexact alarms and says so. `USE_EXACT_ALARM` is deliberately not used. |
| `RECEIVE_BOOT_COMPLETED` | Re-create alarms after a restart (Android clears all alarms on reboot). |
| `WAKE_LOCK`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` *(merged from WorkManager)* | Added automatically by the WorkManager library for its own scheduling; LifePilot itself never uses the network. |

There is **no `INTERNET` permission**. Auto Backup is disabled (`allowBackup=false`) so data is not copied to the cloud; use the in-app JSON backup instead.

## Testing

- **JVM unit tests** (`app/src/test`): reminder policy (intervals, offsets, due-time snapping, snooze, quiet hours incl. overnight windows and per-task windows, daily limits, stop conditions, DST gap & elapsed-time intervals, timezone change, clock moved backwards), recurrence engine, TaskService end-to-end with fakes (repeat until complete, dismiss ≠ complete, snooze ≠ complete, complete cancels alarms, idempotent completion, concurrent complete/snooze, reschedule replaces alarm, edit interval, delete cancels, duplicate, master switch, max per day, quiet-hour deferral, reconcile idempotence, restart/reboot recovery, timezone change, recurring completion/materialization/missed policies/bounded catch-up), habits & streaks, insights, quick-add parser, backup validation, CSV escaping, backup JSON round-trip.
- **Instrumented tests** (`app/src/androidTest`): Room persistence across reopen, unique-occurrence constraint, conditional completion, cascades/category reassignment, exported-schema validation via `MigrationTestHelper`, real Room + TaskService coordination, real AlarmManager PendingIntent identity, notification action broadcasts (snooze/complete) through the real receiver.
- **Compose UI tests**: onboarding, empty states, quick-add, full editor, completing from the list, snoozing from detail, edit + delete, habit creation and check-off, theme switching, editor validation errors.

See **Verification status** below for what was actually executed.

## Known limitations

- **Delivery timing is up to Android.** Without "Alarms & reminders" access, reminders use inexact alarms and can be several minutes late. In Doze, Android limits how often one app's alarms may fire (roughly once per 9 minutes), so very short intervals (5 min) may be spaced out while the phone is idle. Some manufacturers (Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus…) add extra background restrictions; the Reliability screen flags these and links to settings. Do Not Disturb can silence reminders. Nothing in the app claims delivery is guaranteed.
- **Force-stop** (Settings → Force stop) cancels all alarms until the app is opened again — Android does not let apps recover from that automatically.
- **Restore replaces** all data (after validation, with an automatic safety copy saved on the device); merging two datasets is not supported.
- **Natural-language capture** is intentionally narrow (English phrases listed in `QuickAddParser`); everything else uses the pickers.
- **Long absences**: a recurring task back-fills at most 31 days of missed occurrences.
- Habit reminders fire once at the preferred time (they don't repeat like task reminders).
- The release build is signed with the debug key for evaluation; configure your own signing before publishing.
- Database is at schema version 1; the migration test validates the exported schema, and future versions must add a `Migration` (no destructive fallback is configured, by design).

## Privacy

All data lives in the app's private storage on your device. No account, analytics, crash reporting, or ads. Notifications show a generic text on the lock screen. Task text is never logged.
