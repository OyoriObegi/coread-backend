> Current deployment policy: free services until scaling is explicitly requested. FREE_TIER_SETUP.md supersedes historical Firebase Storage and SMTP deployment instructions below.

# CoRead Task List

## Priority Order
1. Create the two workspace directories: `coread-converter` and `coread-android`
2. Build the conversion service and document the Render deployment walkthrough
3. Configure Firebase rules and group schema
4. Implement Room and DataStore persistence
5. Add auth, groups, and deep-linked invites
6. Build reading verification and milestone logic
7. Implement commentary review + streak evaluation
8. Add general chat, reminders, and AI assistant features
9. Run final release verification and report results

## Detailed Task Breakdown
- [x] T1: Set up `coread-converter` project skeleton and local environment
- [x] T2: Implement FastAPI app, PDF parsing, EPUB fallback, and health endpoint
- [x] T3: Add conversion validation, TOC extraction, paragraph segmentation, and JSON packaging
- [x] T4: Integrate Firebase Cloud Storage upload and gzip compression
- [x] T5: Add Dockerfile, requirements, pytest tests, and Render deployment docs
- [x] T6: Configure Firestore and Storage security rules for group isolation
- [x] T7: Define group document schema and member/invite onboarding flow
- [x] T8: Build Room entities and database access layer in `coread-android`
- [x] T9: Add Preferences DataStore and book JSON import pipeline
- [x] T10: Implement Firebase Auth with Google Sign-In and Email Link
- [x] T11: Add app links and group invite deep-link handling
- [x] T12: Build reading screen with paging, validation, and dwell tracking
- [x] T13: Add daily quote long-press verification and Firestore sync batching
- [x] T14: Build milestone preview, pace changes, and pause-request approval flow
- [x] T15: Implement commentary composer, review status, and streak evaluation
- [x] T16: Add general chat, reminder workers, and push notifications
- [x] T17: Integrate OpenRouter AI assistant and prompt limits
- [x] T18: Run release build verification, fix R8/ProGuard issues, and finalize artifact

## Notes
- Android project must be configured for R8 full mode and resource shrinking from the outset.
- The converter must reject scanned image PDFs before any upload occurs.
- Daily reading milestones must rely on verified paragraphs, not simple scroll position.
- Commentary and streak logic must protect users from unfair penalties when a partner is inactive.

## Implementation and deployment status
T1–T17 have source implementations. Parser/API tests and Firebase rule emulator tests pass. Cloud features still need your Firebase/Render configuration and live two-account verification; checked boxes mean implementation, not deployed production acceptance.

Release build, unit-test, size-gate results and known limitations are recorded in VERIFICATION.md. Deployment, production signing and device tests are separate from source completion.

Final automated validation and release-size gate passed. See VERIFICATION.md for measured sizes, test counts and remaining account/device acceptance steps.

Free-first update: Supabase is now the default private book store; Render is explicitly Free; default Firebase deploy excludes Storage. Backend tests: 58 passed. Emulator rules: 20 passed. No live Supabase project or Render deployment has been provisioned.
