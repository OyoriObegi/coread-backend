> Latest update: CoRead 1.0.2 is built and verified. Firestore rules are deployed. The prepared backend must be pushed and manually deployed on Render before server-feature testing. See FEATURE_UPDATE_1_0_2.md and LIVE_BUILD_VERIFICATION.md. Older build notes below are historical.

> Connected build available: CoRead-1.0.1.apk is configured for coread-9bc50 and coread-backend.onrender.com. Use it for device testing. FIREBASE_LINKS_SETUP.md covers the remaining Hosting association/index deployment. Older local-demo build notes below refer to CoRead-preview.apk only.

# CoRead setup: free-first deployment

Follow FREE_TIER_SETUP.md. Keep the current Firebase project coread-9bc50 on Spark. Firebase Storage/Blaze is not required by the default deployment.

- Firebase Authentication, Firestore and Cloud Messaging stay in place.
- Supabase Free private Storage holds converted books.
- Render Free hosts the Python conversion/group API.
- Initial invitations use shareable links rather than SMTP.
- AI is optional and disabled until a free-model key is configured.

Deploy only Firestore:
    firebase deploy --only firestore --project coread-9bc50

Use coread-converter/RENDER_DEPLOYMENT.md for server secrets and hosting. Supply the actual Render hostname before rebuilding the Android app with google-services.json. The earlier preview remains a local demo; it has not been rebuilt with the new live configuration.

Production signing and device/live-service acceptance tests remain separate setup steps. Do not provision paid services without an explicit decision.
