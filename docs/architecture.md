# Architecture

This app should use `Clean Architecture` with `MVVM` at the UI layer.

The goal is to keep Android framework code thin, isolate business rules in pure Kotlin classes, and make storage and SMTP delivery replaceable and testable.

## Architecture Choice

- `MVVM` is the presentation pattern.
- `Clean Architecture` defines the boundaries between UI, domain, data, services, SMTP, and storage.
- Keep one Android app module for now, but structure packages so the code can be split into multiple Gradle modules later.

## Target Package Structure

```text
com.notify2email.app
  data/
    EventRepositoryImpl
    SettingsRepositoryImpl
    ServiceStateRepositoryImpl
    mapper/
  domain/
    model/
      AppSettings
      Event
      EventStatus
      EventType
      PermissionState
      ServiceState
      DeliveryResult
    repository/
      EventRepository
      SettingsRepository
      ServiceStateRepository
    usecase/
      GetDashboardSummaryUseCase
      GetEventHistoryUseCase
      SaveSettingsUseCase
      ToggleServiceUseCase
      TestSmtpUseCase
  services/
    EventForegroundService
    BootReceiver
    collectors/
  smtp/
    SmtpEmailSender
    EventEmailDispatcher
    SmtpConfigProvider
  storage/
    EventHistoryStore
    SharedPreferencesSmtpConfigProvider
  ui/
    dashboard/
      DashboardViewModel
    events/
      EventsViewModel
    settings/
      SettingsViewModel
    permissions/
      PermissionsViewModel
```

## Layer Responsibilities

### UI

- Activities and adapters render state only.
- ViewModels expose screen state and call use cases.
- UI never talks directly to SMTP or raw storage APIs.

### Domain

- Pure Kotlin models, repository interfaces, and use cases.
- No Android framework types.
- Owns validation rules, service toggling rules, event summaries, and SMTP test intent.

### Data

- Implements domain repository interfaces.
- Maps storage models to domain models.
- Coordinates between `storage`, `smtp`, and background services.

### Services

- Android framework entrypoints:
  - `NotificationListenerService`
  - `BroadcastReceiver`
  - call log observer
  - foreground service
- Should capture events and hand off immediately.
- Must not contain business rules beyond minimal lifecycle wiring.

### SMTP

- SMTP transport, TLS mode handling, retries, and dispatch formatting.
- Returns structured results instead of leaking transport exceptions through the app.

### Storage

- Persists settings, counters, event history, and service state.
- SharedPreferences is acceptable for the current stage, but Room + DataStore is the long-term target for production robustness.

## Data Models

### Event

- `id`
- `type`
- `contentPreview`
- `body`
- `timestampMillis`
- `status`

### AppSettings

- `smtpHost`
- `smtpPort`
- `encryption`
- `username`
- `password`
- `fromEmail`
- `toEmail`
- `sendingEnabled`

### ServiceState

- `isRunning`
- `lastUpdatedMillis`

## ViewModels

### DashboardViewModel

- loads service status
- loads SMS/call/notification counters
- loads last sent event
- starts/stops service through a use case

### EventsViewModel

- loads event history
- deletes single event
- clears all events

### SettingsViewModel

- loads current SMTP settings
- validates and saves settings
- triggers SMTP test

### PermissionsViewModel

- evaluates SMS, call log, and notification access states
- exposes which request path to use:
  - runtime dialog
  - system settings screen

## Event Flow

1. Android collector captures raw event.
2. Collector hands event to a dispatcher/repository boundary.
3. Event is normalized into a domain `Event`.
4. Storage persists event history and status.
5. SMTP dispatcher attempts delivery on a background coroutine.
6. Result updates stored status.
7. Dashboard and Events screens render from repository data.

## Initialization Order

1. App launches.
2. Dashboard reads service state and stored counters.
3. Settings screen configures SMTP and permissions.
4. Foreground service starts only when settings are valid and sending is enabled.
5. Collectors run and dispatch events through repositories and SMTP.

## Testing Strategy

- Unit test domain models and use cases.
- Unit test repository implementations with fake storage and fake SMTP sender.
- Instrumentation test Activities, permission flows, and foreground-service startup.
- Keep collectors and services thin so most logic stays JVM-testable.

## Refactor Path From Current Project

- Move `EventHistoryStore` under `storage`.
- Move `SharedPreferencesSmtpConfigProvider` under `storage`.
- Move SMTP-specific classes under `smtp`.
- Introduce repositories and use cases as the only dependencies of ViewModels.
- Replace direct Activity storage access with ViewModel + repository calls.
