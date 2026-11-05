# CoRead verification — 28 October 2026

Source folder: E:\Projects\CoRead

## Results
| Check | Result |
|---|---|
| Python parser/API/regression suite | 40 passed |
| Firebase Firestore/Storage emulator rules | 20 passed |
| Android dwell-time unit tests | 4 passed, zero failures/errors |
| Android debug APK | Built successfully |
| Android minified release APK | Built successfully |
| Release vital lint | Passed |
| R8 full mode and resource shrinking | Enabled; release completed |
| Explicit release size gate | Passed, limit 5,000,000 bytes |
| Unsigned release APK | 3,379,641 bytes |
| Installable preview APK | 3,433,736 bytes (3.43 MB) |

The combined automated suite has 64 passing tests. Python tests include mocked Firebase/Storage API integration; Firebase security tests run against actual local emulators. No cloud accounts are required for those tests.

## Preview
CoRead-preview.apk is the minified release build signed with a local development/debug certificate for installation and testing. It is not production signed. The local sample book works without Firebase configuration. Uninstall this preview before installing a production build signed with a different key.

SHA-256: 379ddbcd2c3a8a0ef783acf542938ceb83e477ca2763670a0aa69ce7f126bc5f

## Implemented
- Private PDF/EPUB conversion; real font-size chapter inference, paragraph-index chapter markers, scan/corrupt-file validation, bounded uploads and private gzip Storage objects.
- Authenticated group creation/invitation/join, partner-only pace/pause approvals, monthly pause budget, reflection/revision/review transactions and idempotent streak evaluation.
- Native Compose reader, Room/Paging cache, streamed transactional import, per-account/group progress, foreground dwell checks, quote gating, background/exit sync and session statistics.
- Google/email-link authentication, App Links, group chat, FCM/nudges, habit reminder worker, and server-side OpenRouter questions, summaries and mediation.
- Previous-day review access and 12-hour revision handling after the group advances; mediation uses the selected day's context.
- Firebase isolation/spoiler rules, deployment templates, setup guide and CI jobs.

## Commands verified
- python -m pytest coread-converter -q
- firebase emulators:exec --only firestore,storage --project demo-coread "node --test --test-concurrency=1 firebase/tests/rules.test.cjs"
- Gradle testDebugUnitTest assembleDebug assembleRelease checkReleaseSize
- apksigner verify --verbose CoRead-preview.apk

Build toolchain: Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, JDK 21, compile/target SDK 35, min SDK 26. Python tests used Python 3.13.3; requirements.txt pins the tested installed library versions.

## Still requires your service setup
No Firebase project or Render service has been provisioned, as requested. Follow SETUP.md for Firebase, Render, domains/App Links, signing certificates, optional SMTP and OpenRouter credentials. Production service secrets and signing keys are not bundled.

## Device and live acceptance checks
No Android device or configured Android emulator was available. APK installation/startup, visual layout, scrolling performance, minified-runtime reflection paths, notifications, Google/email-link sign-in and the live two-account workflow still need device testing. Successful compilation and R8 do not establish runtime behavior.

After configuration, use two verified-email accounts to create/join a circle, import a book, verify contiguous reading, save a quote, submit and review reflections, test revision grace/history, approve/reject pace and pause requests, complete a day, exchange chat messages, and request AI/nudges.

## Operational limits
One active book per circle; create another circle for the next book. WorkManager reminders are approximate. Dwell checks guide normal app behavior and cannot prove reading against a modified client. An interrupted import may require clearing importingBookId in the Firebase console before retrying.
