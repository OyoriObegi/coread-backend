# CoRead setup checkpoint — 29 October 2026

- Latest APK: CoRead 1.0.3, version code 4, 3,450,176 bytes; same development signing certificate.
- Progress-sync fix: merges server/local verified progress transactionally, waits for book loading, checks book identity, and clears the old warning after a successful retry. No live progress was reset.
- Verification: 9 Android unit tests and 23 Firebase security-rule tests passed. Release build, lint, shrinking, size check and APK signature/configuration checks passed.
- Firestore rules: deployed and independently confirmed to match the tested rules. No rule changes are needed for this app fix.
- Backend: unchanged by the sync fix; its 85 tests previously passed.
- Install CoRead 1.0.3 over the existing app, open the affected book, wait about 30 seconds and switch tabs. Confirm the repeated permission warning stops and the saved reading location remains intact.
- Earlier feature update: creator circle/book controls, request/reflection recall and first-chapter goals are prepared in the public backend package, but its GitHub push and Render deployment are still pending. Follow CoRead-1.0.2-Update.md for that separate step; install 1.0.3 instead of 1.0.2.
- Google sign-in previously worked; email-link sign-in and private book/two-partner flows still require phone testing.
- Existing free-service configuration is retained. No paid service was enabled.
