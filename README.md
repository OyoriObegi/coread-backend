# Free-first deployment update
Keep Firebase on Spark. Book storage now defaults to Supabase Free and the server blueprint explicitly selects Render Free. Do not follow older Firebase Storage/Blaze instructions. See FREE_TIER_SETUP.md.

# CoRead

Native Android reading app for two partners, with a private conversion and group API.

## Implemented
- Compose/Material 3 app: Home, Read, Reflect, Chat, Insights and Settings; warm palette and book/flame adaptive icon.
- Offline sample book; Room/Paging cache; streamed, transactional imports; DataStore settings.
- Foreground/recent-interaction dwell verification, contiguous reading progress, quote saving, session statistics and batched sync.
- Google/email-link authentication, group invitations, PDF/EPUB upload, partner approval of pace/pause requests.
- Reflection/review/revision workflows, streak evaluation, FCM, habit reminders and OpenRouter AI features.
- Authenticated private books, Firebase group rules, parser/API and Kotlin tests, CI workflow.

## Try it
Open coread-android in Android Studio using JDK 17/21 and SDK 35. Run gradlew.bat assembleDebug and install app/build/outputs/apk/debug/app-debug.apk. The sample works without Firebase configuration.

## Shared reading
Follow FREE_TIER_SETUP.md and coread-converter/RENDER_DEPLOYMENT.md. Firebase project coread-9bc50 and sign-in providers are configured. Supabase Storage and Render deployment remain to be set up; see FREE_TIER_SETUP.md.

See VERIFICATION.md for measured results and remaining limits. R8 full mode/resource shrinking are enabled. The 5 MB release budget is enforced by a separate Gradle task and must be measured on the final configured app.
