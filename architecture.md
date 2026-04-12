# Architecture: Phone Events to Email

This document describes the architectural patterns and structure of the Phone Events to Email Android application.

## Overview

The application follows a layered architecture, influenced by Clean Architecture principles, ensuring separation of concerns and testability.

## Layers

### 1. Presentation Layer (`com.notify2email.app.ui`)
- **Pattern**: MVVM (Model-View-ViewModel).
- **Components**:
    - **ViewModels**: Manage UI state using Kotlin Flows and handle user interactions.
    - **UI Components**: Jetpack Compose based screens and components.
    - **Navigation**: Defines the app's navigation graph.
- **Dependency Injection**: ViewModels are instantiated via `AppViewModelFactory`, which receives dependencies from `AppContainer`.

### 2. Domain Layer (`com.notify2email.app.domain`)
- **Models**: Plain Kotlin data classes representing core entities (e.g., `Event`, `AppLog`, `SmtpSettings`).
- **Repositories (Interfaces)**: Abstractions for data operations, allowing the domain layer to remain independent of data sources.
- **Formatters**: Logic for converting domain models into human-readable strings.

### 3. Data Layer (`com.notify2email.app.data`, `com.notify2email.app.storage`)
- **Repository Implementations**: Concrete classes that implement domain interfaces (e.g., `RoomEventRepository`, `SharedPreferencesSettingsRepository`).
- **Local Storage**:
    - **Room**: Persistent storage for events and application logs.
    - **SharedPreferences**: Storage for user settings and service state.

### 4. Service & Collector Layer
- **Foreground Service (`EventForegroundService`)**: Manages the lifecycle of event collection while the app is in the background.
- **Collectors (`com.notify2email.app.collectors`)**: Specific logic for observing system events (SMS, Call Logs).
- **Notification Listener (`PhoneNotificationListenerService`)**: Android service for intercepting system notifications.
- **Receivers (`com.notify2email.app.receivers`)**: BroadcastReceivers for system events like device boot.

### 5. Email Module (`com.notify2email.app.email`)
- **SMTP Sender**: Logic for sending emails using SMTP.
- **Batching (`EventBatchQueueManager`)**: Manages a queue of events to be sent in batches, ensuring efficiency and handling retry logic.

## Dependency Injection

The project uses manual dependency injection via an `AppContainer` class, initialized in the `PhoneEventsApp` (Application class). This container holds long-lived dependencies and provides them to ViewModels and services.

## Data Flow

1.  **Collection**: Collectors and Services detect system events (SMS, Call, Notification).
2.  **Storage**: Events are saved to the `EventRepository`.
3.  **Queueing**: The `EventBatchQueueManager` observes the repository or is notified of new events.
4.  **Transmission**: Events are batched and sent via `SmtpEmailSender`.
5.  **UI Update**: ViewModels observe repositories via Kotlin Flows and update the UI state accordingly.

## Detection Mechanisms

### 1. SMS Detection
- **Mechanism**: `SmsReceiver` (BroadcastReceiver).
- **Requirements**:
    - `android.Manifest.permission.RECEIVE_SMS` permission.
    - Global "Service Status" must be ON in the app.
    - "SMS Forwarding" must be enabled in SMTP settings.
- **Workflow**:
    - The system broadcasts `SMS_RECEIVED_ACTION`.
    - `SmsReceiver` captures the PDUs, reconstructs the message, and extracts the sender/timestamp.
    - A SHA-256 deduplication key is generated based on sender, timestamp, and body.
    - The event is enqueued in `EventBatchQueueManager`.

### 2. Call Detection
- **Mechanism**: `CallLogObserver` (ContentObserver).
- **Requirements**:
    - `android.Manifest.permission.READ_CALL_LOG` permission.
    - Global "Service Status" must be ON.
    - "Call Log Forwarding" must be enabled in SMTP settings.
- **Workflow**:
    - `CallLogObserver` monitors `CallLog.Calls.CONTENT_URI`.
    - Upon change, it queries the Call Log for new entries with an ID greater than the `lastProcessedEntryId` (stored in SharedPreferences).
    - It filters for specific types: `MISSED`, `INCOMING`, `OUTGOING`, and `REJECTED`.
    - To handle slow system writes, it includes a 1.5s delay and a retry mechanism.
    - Events are deduplicated and enqueued.

### 3. Notification Detection
- **Mechanism**: `PhoneNotificationListenerService` (NotificationListenerService).
- **Requirements**:
    - **Notification Access** must be manually granted by the user in Android System Settings.
    - Global "Service Status" must be ON.
    - "Notification Forwarding" must be enabled in SMTP settings.
- **Exceptions & Filtering**:
    - **Self-Filtering**: Notifications from the app itself are ignored.
    - **Category Filtering**: `CATEGORY_CALL` and `CATEGORY_MISSED_CALL` are ignored to prevent redundancy with the Call collector.
    - **Noise Filtering**: "Syncing", "Checking for messages", and ongoing service notifications (e.g., progress bars) are filtered out.
    - **Smart Labeling**: Notifications from known SMS apps (e.g., Google Messages) or Dialers are automatically relabeled as `SMS` or `CALL` types.
    - **User Filters**: Supports Whitelist/Blacklist modes for specific applications.
    - **Debouncing**: Includes a 750ms debounce delay before processing to ensure notification content is fully populated.

## Navigation Map

The application uses Jetpack Compose Navigation with the following paths:

- **Dashboard** (`dashboard`): The landing screen showing service status, quick toggles, and sync statistics.
    - Navigates to **Settings** (can specify a target tab).
- **Events** (`events`): Displays a history of captured events and their delivery status.
- **Settings** (`settings?tab={tab}`): Configuration for SMTP, Notification Filters, and App Theme.
    - Navigates to **Logs**.
- **Logs** (`logs`): A diagnostic screen showing internal application logs and system triggers.

**Common Navigation Flow**:
`Dashboard` -> `Settings` -> `Logs`
`BottomBar` -> `Dashboard` | `Events` | `Settings`

## Consistency Check

- **Dependency Direction**: UI -> Domain <- Data. All UI components depend on Domain interfaces, not concrete Data implementations.
- **Model Isolation**: Domain models are used across layers, avoiding leakage of Room entities or SharedPreferences keys into the UI.
- **Threading**: Heavy operations (database, network) are handled in Coroutine scopes with appropriate Dispatchers (IO).
