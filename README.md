# CoRead backend

FastAPI converter and group API for the CoRead Android app.

## Deployment
This repository contains the backend files at its root. On Render choose:
- Service: Web Service
- Source: Public Git Repository URL
- Runtime: Docker
- Root directory: leave blank
- Instance: Free
- Health check: /health

The included blueprint also explicitly selects the Free plan.

## Environment variables
Enter these directly in Render Environment, outside this repository:
- FIREBASE_PROJECT_ID=coread-9bc50
- FIREBASE_SERVICE_ACCOUNT_JSON: your Firebase Admin JSON, server only
- BOOK_STORAGE_PROVIDER=supabase
- SUPABASE_URL=https://viopsurlelonnpwrocbl.supabase.co
- SUPABASE_SECRET_KEY: your server secret, never a mobile/publishable key
- SUPABASE_STORAGE_BUCKET=coread-books

Keep the Supabase bucket private. CoRead checks Firebase identity and group membership before accessing book objects. This configuration uses Firebase Spark, Supabase Free and Render Free.

APP_LINK_ORIGIN and ANDROID_CERT_SHA256 are configured once the Render hostname and app signing certificate are finalized. Leave SMTP and AI settings unset initially. Free services impose quotas and can sleep or pause.

GET /health is public. All group/book API operations require verified-email Firebase authentication. Uploaded PDFs/EPUBs are converted into gzip JSON stored in the private bucket. No service credentials, user data or book files are included in this source package.


## Circle controls
The circle creator can rename or permanently delete their circle, remove a book and then import a replacement. Existing circles use the original first member as their creator until ownerId is recorded. The server enforces these permissions.

Removing a book deletes its private storage object and resets both readers' progress. Shared chat stays in the circle. Earlier reflections retain unique day numbers and cannot satisfy the replacement book's goals. Circle deletion removes the private book, members, messages, reflections and nudges. Failed cleanup can be retried through the same control.

Requesters can cancel pending pace/pause requests. Responses include the request identifier, so a stale screen cannot approve a newer request. Authors can recall pending reflections before review or day completion.

New imports start daily goals at a detected Chapter One/Part One marker. Preliminary pages remain readable. If no reliable marker is found the server keeps the start at the beginning. For an existing book the creator can move its first daily goal to the detected chapter before either partner submits a reflection.

The Android app stores reading position separately from verified progress, per account, circle and book. This is local device storage and does not synchronize the scroll position between devices.

## Updating an existing service
After pushing this update, use Render Manual Deploy > Deploy latest commit when deploying from a public Git URL. Deploy the matching Firestore rules and install CoRead 1.0.2. No new environment variables, billing plan changes or paid services are required.
