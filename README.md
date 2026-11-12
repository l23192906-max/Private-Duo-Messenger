# OUR CHAT

OUR CHAT is a two-account messaging app with a native Kotlin Android client and a Node.js, Express, Socket.IO, and SQLite backend. The Android app supports Android 8.0 (API 26) and newer.

## What is included

- Exactly two server-created roles: ADMIN and GUEST. There is no public registration.
- First-run setup for both usernames; the first device is signed in as ADMIN.
- Persistent messages, timestamps, sent/delivered/read ticks, live Socket.IO updates, and online presence.
- An ADMIN dashboard with confirmed internet state, network type, server update freshness, app foreground/background state, service state, diagnostics, histories, and daily/weekly session summaries.
- An ADMIN control to show or hide the GUEST's **SHOW CONNECTION STATUS** button.
- A GUEST consent screen, Android foreground-service notice, status-sharing display, and a stop-sharing control that remains available even if ADMIN hides the button.
- Android connectivity callbacks and approximately 15-second heartbeats while the service and network are available.
- SQLite-backed event history. A missing heartbeat becomes **STALE**, never a confirmed internet disconnection.

## First setup

1. Start the configured **API Server** workflow in Replit. It serves the API under `/api`.
2. Install the Android app on the ADMIN device and enter the server's HTTPS origin (for example, `https://your-host.example`, without `/api`).
3. On the first connection, choose distinct ADMIN and GUEST usernames. Setup is one-time. The first device is then signed in as ADMIN.
4. Install the app on the GUEST device, enter the same server address, and sign in with the GUEST username.
5. In the ADMIN dashboard, show the status button. On GUEST, tap **SHOW CONNECTION STATUS**, review the disclosure, and explicitly agree before notifications or monitoring begin.

Usernames must be 3–24 letters, numbers, dots, dashes, or underscores.

## Security limits

Username-only sign-in is not identity verification. Anyone who knows either username can sign in as that account and obtain its backend-issued role token. The token prevents a client from simply changing its role field, but it cannot prove who is holding the device.

Initial setup has no password either. Anyone who reaches the uninitialized setup endpoint first can claim both account names. Initialize the server privately before sharing its address.

Messages and event history are stored as readable text in the server's SQLite database. There is no end-to-end encryption. Do not use this build for sensitive or high-risk communication.

## Build and install the Android APK

The project includes a Gradle wrapper. Build on a computer with Android Studio or a Java 17+ toolchain and Android SDK Platform 35 installed. Android Studio will prompt for any SDK license acceptance:

```bash
cd android
./gradlew assembleDebug
```

The debug APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Transfer that APK to each Android device, allow installation from that file source when prompted, and install it. For Android Studio, open the `android/` directory as the project and use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The app uses HTTPS addresses only. Set the server origin in the app; it adds `/api/our-chat` automatically. It requires no API key.

## Run the backend in Replit

The existing Replit API workflow runs the service on its managed port. The equivalent development command is:

```bash
pnpm --filter @workspace/api-server run dev
```

The initial SQLite file is created at `data/our-chat.sqlite` relative to the server working directory. Set `OUR_CHAT_DB_PATH` to choose another file, or `OUR_CHAT_DATA_DIR` to choose its parent directory. Keep the database file out of source control and back it up securely.

The current project is not published as a production app. Replit's free Starter hosting is not a reliable always-on service for this use: published apps can expire and require republishing, Autoscale can sleep after inactivity, and a published app's local filesystem is not durable across redeployments. That makes a local SQLite file unsuitable for reliable published message history. The development URL may also be temporary or private. No paid plan or subscription has been enabled.

For an actual two-device deployment, the Android clients need a reachable HTTPS API and Socket.IO endpoint plus durable storage. Do not expose the temporary development URL as a production service. Replit's current hosting and storage details:

- [Starter plan](https://docs.replit.com/billing/plans/starter-plan)
- [Deployment types](https://docs.replit.com/features/publishing/deployment-types)
- [Storage and databases](https://docs.replit.com/learn/projects-and-artifacts/storage-and-databases)

## Known limitations

- Android background monitoring is best-effort. Android Doze, battery optimization, force-stop, manufacturer-specific background limits, network loss, and server downtime can delay or stop updates. **STALE** means no recent heartbeat; it does not mean offline.
- Android requires an ongoing foreground-service notification while sharing is active. The app requests notification permission only after GUEST agrees to share. If notification access is disabled, monitoring stops or cannot start.
- The service uses the standard `dataSync` foreground-service type and targets API 34 for sideloading. Android 15 restrictions and OEM rules can still affect background work. The app does not promise uninterrupted 15-second updates.
- ADMIN alerts are local Android notifications delivered while the ADMIN app can receive live server events. No Firebase Cloud Messaging project is configured, so remote push while the app is stopped is not available. Events remain in the dashboard history.
- The Replit environment has no Android SDK or physical device. The APK has not been built here, and Samsung, Xiaomi/Redmi, Oppo, Vivo, Huawei, Motorola, and other manufacturers have not been physically tested.
- Android 15+ and OEM-specific service startup, reboot recovery, notification behavior, Wi-Fi/mobile handoff, and battery settings still require device testing.

## Device test checklist

On at least one Android 8–14 device and one Android 15+ device, test:

1. Install, first-run setup, ADMIN/GUEST sign-in, chat delivery/read indicators, and reconnect synchronization.
2. Guest consent disclosure, visible foreground notification, active-sharing display, and revoke behavior.
3. ADMIN show/hide control while sharing is off and while sharing is active.
4. Wi-Fi to mobile-data changes, airplane mode, internet restoration, and server downtime. Confirm missing updates become **STALE**, not **OFFLINE_CONFIRMED**.
5. Put GUEST in the background, enable battery restrictions, revoke notification access, reboot, and observe whether the service is paused or recovered.
6. Repeat on Samsung, Xiaomi/Redmi, Oppo, Vivo, Huawei, and Motorola devices because their battery managers differ.

## Source layout

- `android/` — native Kotlin app, foreground monitoring service, and Gradle build.
- `artifacts/api-server/` — Express routes, Socket.IO server, and SQLite store.
- `artifacts/api-server/src/our-chat/store.ts` — account, message, monitoring, session, and event persistence.

