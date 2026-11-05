# Finish free Firebase Hosting and index setup

This prepares Android email-link handling and the two Firestore query indexes. It does not create Firebase Storage or require Blaze.

Files are prepared in E:\Projects\CoRead:
- firebase.json: Firestore and static Hosting configuration.
- firebase/firestore.indexes.json: commentary queries.
- firebase/hosting/public/.well-known/assetlinks.json: actual Android certificate association.
- firebase/hosting/public/index.html and invite/index.html: sign-in fallback text.

Run from E:\Projects\CoRead:
    npx --yes firebase-tools@14.2.1 login
    npx --yes firebase-tools@14.2.1 deploy --only firestore,hosting --project coread-9bc50

Use the Google account that owns coread-9bc50. Complete the browser login yourself. The deployment includes Firestore rules/indexes and static Hosting only. Do not run firebase init or overwrite the prepared configuration. Do not deploy the optional Firebase Storage emulator target.

After deployment:
- https://coread-9bc50.firebaseapp.com/.well-known/assetlinks.json should return the certificate association.
- Both commentary indexes should finish building in Firestore → Indexes.
- Google's reserved /__/auth/links handler must remain available.
- If the APK was installed before the association file was deployed, reinstall the APK to trigger Android link verification again.
- Test an email-link sign-in on the same Android device where you requested it.

No Firebase Admin JSON or Supabase key is needed in the public Hosting folder or in chat. This deployment step still requires your CLI login and has not been run automatically.
