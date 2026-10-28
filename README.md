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
