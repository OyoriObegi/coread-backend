# Render Free deployment

Use FREE_TIER_SETUP.md in the project root for the complete free stack. Do not create Firebase Storage or upgrade Firebase to Blaze for this configuration.

## Create the server
1. Push the source to your Git repository.
2. Create a Render Web Service using Docker and root directory coread-converter.
3. Explicitly select the Free compute instance. The root render.yaml also pins plan: free.
4. Set health check path /health. The Docker command honors Render's PORT.

## Server-only environment
- FIREBASE_PROJECT_ID=coread-9bc50
- FIREBASE_SERVICE_ACCOUNT_JSON: Firebase service account JSON for Auth token verification, Firestore and FCM.
- BOOK_STORAGE_PROVIDER=supabase
- SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
- SUPABASE_SECRET_KEY: backend secret starting sb_secret_. Alternatively use SUPABASE_SERVICE_ROLE_KEY for a legacy service_role JWT.
- SUPABASE_STORAGE_BUCKET=coread-books
- OPENROUTER_API_KEY: OpenRouter API key, stored only as a Render secret.
- OPENROUTER_MODEL=openrouter/free

Create coread-books in Supabase Storage with Public OFF and no anonymous policies. Firebase membership is checked before the server accesses any object. Supabase keys are never returned to the app.

Set APP_LINK_ORIGIN to the free Render service URL. Set ANDROID_CERT_SHA256 to the signing fingerprint for Android fallback links, and update the Android fallback host before rebuilding. Users can also copy/paste a group ID to join.

## Optional features
Leave SMTP settings unset: Render Free blocks outbound SMTP ports 25, 465 and 587. Group creation returns emailSent=false plus a shareable invite URL. Firebase authentication emails are independent of this and have their own Spark quota.

AI requires `OPENROUTER_API_KEY`; without it, `/groups/{groupId}/ai` returns 503 by design. Add the key in Render's environment settings or from the `render.yaml` secret prompt, then redeploy. Keep `OPENROUTER_MODEL=openrouter/free` unless OpenRouter reports that the router is unavailable.

## Local verification
Install requirements.txt, load server environment variables through your shell/IDE, and run uvicorn main:app --reload. The service does not automatically load .env.
Run python -m pytest -q.

For free deployments, identity verification initializes Firebase without requiring any Firebase Storage bucket. Google Storage is selected only if BOOK_STORAGE_PROVIDER is explicitly changed to firebase. There is no automatic paid-provider fallback.

## Limits and acceptance
Render Free sleeps when idle and loses local files on restart; permanent book files live in Supabase, not Render's filesystem. Supabase Free and Render Free both impose quotas and may pause/suspend service. Keep all plans Free/Spark until you choose otherwise.

No live deployment was created by this change. After configuration, test two-account auth, invite/join, conversion/download, review and notifications on Android. The original APK preview needs rebuilding with current Firebase config and the actual service hostname.
