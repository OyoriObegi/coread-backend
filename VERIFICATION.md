> Latest update: CoRead 1.0.2 is built and verified. Firestore rules are deployed. The prepared backend must be pushed and manually deployed on Render before server-feature testing. See FEATURE_UPDATE_1_0_2.md and LIVE_BUILD_VERIFICATION.md. Older build notes below are historical.

# Latest connected Android build
CoRead-1.0.1.apk now includes Firebase project coread-9bc50 and https://coread-backend.onrender.com. See LIVE_BUILD_VERIFICATION.md for current checks. Its packaged config/signature/size and unit tests passed; phone sign-in and live book conversion still require device testing. Firebase Hosting association and commentary indexes are prepared but await deployment.

The older local-demo APK remains available separately. Previous free-backend verification follows.

# CoRead free-first verification — 28 October 2026

Source: E:\Projects\CoRead

## Current change
Default book storage is private Supabase Storage. Firebase remains on Spark for Auth, Firestore and FCM. The Render blueprint explicitly selects plan: free. Default Firebase deployment has no Storage target; its test-only configuration is .firebase-emulators.json.

No cloud service was provisioned, no billing account was linked, and no paid plan was enabled by this change.

## Checks run for this change
| Check | Result |
|---|---|
| Backend parser, API, streak and storage adapter suite | 58 passed |
| Firestore and optional legacy Storage rule emulator suite | 20 passed |
| Docker source packaging | Includes main.py and book_storage.py |
| Render default | Free compute, Supabase provider |
| Default Firebase deployment | Firestore only |

Storage tests cover private upload/download routes, new secret and legacy JWT headers, safe provider errors, missing objects, invalid configuration, traversal rejection, no paid fallback, and avoiding duplicate downloads. HTTP mocks establish adapter behavior; live Supabase credentials and a project are still needed for end-to-end verification.

## Android baseline
Android source/API download routes did not change for this storage migration. The earlier debug/release build and four dwell unit tests passed; the signed preview is 3,433,736 bytes, below 5 MB. Its build report is retained in VERIFICATION_ANDROID_BASELINE.md.

The preview is still the local-demo build from before the live Firebase configuration was added. It has not been rebuilt with coread-9bc50 and the final Render hostname. Device, Google sign-in, email-link, live Supabase and full two-account tests remain pending setup.

## Current setup state
Firebase project coread-9bc50 exists; Google and email-link providers are enabled. Android configuration with Google OAuth is copied into the app module. The user reports Firestore nam5 and custom rules published. Keep Spark and skip the Firebase Storage upgrade.

Next: follow FREE_TIER_SETUP.md to create Supabase Free private bucket coread-books, then deploy the API on Render Free. Server keys stay in backend environment secrets.

## Free-tier limits
Supabase Free currently includes 1 GB Storage and 5 GB egress; projects may pause after inactivity. Render Free sleeps and imposes usage/suspension limits. Firebase Spark email-link sending is limited to 5 emails/day; use Google sign-in primarily. Initial group invitations use shareable URLs because Render Free blocks ordinary SMTP.

No paid-provider fallback or automatic paid scaling is configured. Free tiers can change; review actual quotas when usage grows.

See FREE_TIER_SETUP.md and coread-converter/RENDER_DEPLOYMENT.md.
