> Latest update: CoRead 1.0.2 is built and verified. Firestore rules are deployed. The prepared backend must be pushed and manually deployed on Render before server-feature testing. See FEATURE_UPDATE_1_0_2.md and LIVE_BUILD_VERIFICATION.md. Older build notes below are historical.

> Connected build available: CoRead-1.0.1.apk is configured for coread-9bc50 and coread-backend.onrender.com. Use it for device testing. FIREBASE_LINKS_SETUP.md covers the remaining Hosting association/index deployment. Older local-demo build notes below refer to CoRead-preview.apk only.

# CoRead: start on free services

Current policy: keep Firebase on Spark, Supabase on Free and Render on Free. Do not enable Blaze, paid instances, paid add-ons or purchased domains to follow this guide. All third-party quotas can change; this is a free pilot configuration, not a promise of unlimited hosting.

## What you already completed
- Firebase project coread-9bc50.
- Android app com.coread.app and preview signing fingerprints.
- Google sign-in and email-link provider enabled.
- Updated google-services.json copied into the app module.
- Firestore created in nam5 and CoRead Firestore rules published.

Keep those settings. Skip Firebase Storage entirely. No Storage bucket or Blaze upgrade is needed.

## Free stack and current limits
| Component | Service | Pilot limit/behavior |
|---|---|---|
| Sign-in | Firebase Spark | Use Google sign-in primarily; email-link sending is limited to 5 emails/day |
| Groups, chat, progress | Firestore Spark | 1 GiB data; 50,000 reads/day; 20,000 writes/day |
| Android push | Firebase Cloud Messaging | No-cost |
| Compressed book files | Supabase Free private Storage | 1 GB file storage; 5 GB egress included; free projects may pause after one week inactive |
| Converter and group API | Render Free | 750 free instance hours/workspace/month; sleeps after 15 minutes idle; quotas and suspension limits apply |
| Invitation delivery | Copy/share the generated invite URL | No mail service required; Render Free blocks ordinary SMTP ports |
| AI | Optional OpenRouter free router | Leave disabled initially, or use only an available free model within provider quotas |

Supabase storage is accessed only by the backend. Your Firebase users do not need Supabase accounts. No public bucket, anonymous Storage policies or Supabase credentials belong in the Android app.

## Step 1: Supabase project
1. Open https://supabase.com/dashboard and sign in or create an account.
2. Create an organization using the Free plan.
3. Create a project named CoRead. Choose and keep a strong database password, even though CoRead only uses Storage.
4. Choose an offered region; a US region is a reasonable starting choice alongside nam5 and a US Render service.
5. Wait for the project to become ready. If a screen asks for a paid plan or payment to proceed, stop and check the exact option selected.

## Step 2: Private book bucket
1. Open Storage and create a bucket named coread-books.
2. Leave Public bucket OFF.
3. Do not add public/anonymous read or write policies.
4. Use the dashboard's supported file-size limit (up to the Free plan's current per-file cap). Stored objects are gzip-compressed book JSON, not the original PDF.
5. Copy the project URL and a backend secret key from the project's API keys/Connect settings. The preferred key starts with sb_secret_. A legacy service_role JWT is also supported.
6. Store that key only as a Render environment secret. Do not put it in google-services.json, Android source, Git or chat.

## Step 3: Render Free backend
Follow coread-converter/RENDER_DEPLOYMENT.md. The included render.yaml explicitly chooses plan: free.

Required environment:
- FIREBASE_PROJECT_ID=coread-9bc50
- FIREBASE_SERVICE_ACCOUNT_JSON: your Firebase Admin service account JSON (server only)
- BOOK_STORAGE_PROVIDER=supabase
- SUPABASE_URL: your project HTTPS URL
- SUPABASE_SECRET_KEY: backend-only secret key
- SUPABASE_STORAGE_BUCKET=coread-books
- OPENROUTER_API_KEY: backend-only OpenRouter API key (required for AI)
- OPENROUTER_MODEL=openrouter/free

Leave SMTP unset. Set `OPENROUTER_API_KEY` as a Render-only secret to enable AI, and set `APP_LINK_ORIGIN` to your free Render HTTPS hostname. No custom domain is required.

## Step 4: Firebase rules and indexes
Only deploy Firestore:
    firebase deploy --only firestore --project coread-9bc50

Default firebase.json intentionally has no Firebase Storage deployment target. Optional legacy Firebase Storage rules remain available solely in .firebase-emulators.json for tests or a deliberate future migration.

## Step 5: Connect and rebuild Android
Send the Render URL once the service exists. The app's fallback invitation host must match it before rebuilding; Firebase email links use coread-9bc50.firebaseapp.com. Add the app signing fingerprint to the backend ANDROID_CERT_SHA256 setting.

The existing 3.43 MB preview is still the earlier local-demo build. It must be rebuilt with your Firebase configuration and final Render hostname before live sign-in/deep-link acceptance tests.

## When limits are reached
Do not automatically upgrade plans. Check actual usage and choose whether to reduce usage, resume a paused free project, migrate, or explicitly approve paid scaling. The backend has no automatic fallback from Supabase to Firebase Storage, and the blueprint does not select paid compute.

## Official references
- [Firebase pricing](https://firebase.google.com/pricing)
- [Firebase email limits](https://firebase.google.com/docs/auth/limits)
- [Supabase Free pricing](https://supabase.com/pricing)
- [Private Supabase buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals)
- [Supabase server keys](https://supabase.com/docs/guides/getting-started/api-keys)
- [Render Free limits](https://render.com/docs/free)
