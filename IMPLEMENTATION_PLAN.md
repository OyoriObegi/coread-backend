> Current deployment policy: free services until scaling is explicitly requested. FREE_TIER_SETUP.md supersedes historical Firebase Storage and SMTP deployment instructions below.

# CoRead Implementation Plan

## 1. Project Goal
CoRead is a dual-workspace mobile + cloud product that helps two reading partners stay synchronized on a shared book while preserving genuine reading behavior. The system combines a stateless conversion service on Render, a Firebase backend for identity and group synchronization, and a Kotlin/Compose Android app that stores local reading progress and syncs verified milestones.

## 2. Workspace Split
- `coread-converter/` — Python service running on Render for PDF/EPUB conversion and import processing.
- `coread-android/` — Kotlin app using Modern Android Development (Kotlin, Jetpack Compose, Material 3, Coroutines, StateFlow, Room, and Hilt).

## 3. Mandatory Android Constraints
Before implementation, configure the Android app to:
- enable R8 full mode
- enable resource shrinking
- keep the release APK under 5 MB for the shipped build
- use a warm Material 3 palette and adaptive launcher icon with overlapping book pages and a flame motif

## 4. Architecture Overview
- Cloud conversion service: FastAPI + PyMuPDF + ebooklib + BeautifulSoup4
- Storage/sync layer: Firebase Authentication, Firestore, Cloud Storage, Cloud Messaging
- Local persistence: Room + Preferences DataStore
- UI layer: Jetpack Compose + Material 3 + LazyColumn paging + paging-based reading flow
- Dependency management: Hilt
- Productivity logic: verified paragraph dwell tracking, milestone previews, streak checks, daily reminder scheduling

## 5. Implementation Phases

### Phase 1 — Stateless Conversion Service (`coread-converter`)
Deliverables:
- `main.py` with FastAPI app
- `GET /health` returning HTTP 200
- `POST /convert` accepting multipart upload, `groupId`, `bookId`
- PDF detection, unsupported scanned-image PDFs (422)
- TOC extraction and heading inference fallback
- paragraph segmentation, word counts, metadata generation
- gzip compression + Firebase Storage upload to `groups/{groupId}/books/{bookId}.json`
- `Dockerfile` and `requirements.txt`
- local verification via `pytest`
- Render deployment walkthrough artifact

### Phase 2 — Firebase Backend and Security Rules
Deliverables:
- `firestore.rules` enforcing `request.auth.uid in resource.data.memberIds`
- `storage.rules` enforcing same isolation logic and invite-based onboarding
- group document schema with reading range, streak, pause, milestone metadata
- subcollections: members, commentaries, messages
- onboarding flow to move invited email into `memberIds` through a transaction

### Phase 3 — Android Data Layer
Deliverables:
- Room database with `BookParagraphEntity`, `ChapterMarkerEntity`, and `ReadingSessionLogEntity`
- Preferences DataStore for small app settings
- conversion health ping before upload
- single-transaction JSON import from downloaded compressed book
- session logging for reading metrics and local analytics

### Phase 4 — Authentication, Group Creation, and Deep Links
Deliverables:
- Firebase Authentication with Google Sign-In and Email Link
- Firestore group creation flow
- invite writing and lightweight email dispatch
- Android App Links for `https://coread.app/invite?groupId={id}` and Render fallback host
- deep-link entry handling in `MainActivity`
- transaction to add invited user to `memberIds` and open the group dashboard

### Phase 5 — Compose Reading Engine and Anti-Cheating Verification
Deliverables:
- `LazyColumn` reading experience backed by Room paging
- dwell-time verification tied to visible paragraph list state
- background timer only active in `RESUMED` and recent user interaction
- paragraph verification gating and amber warning state for skipped paragraphs
- daily quote long-press verification and Firestore sync batching

### Phase 6 — Milestone Controller and Mutual Streak Pause System
Deliverables:
- milestone adjustment sheet with segmented mode selector and continuous slider
- preview of dynamic paragraph end position based on `SUM(wordCount)` in Room
- Firestore pending pace / pause request objects
- partner approval/rejection banner and UI state handling
- approved pause request logic and synchronized pause state updates

### Phase 7 — Commentary, Critique, and Streak Evaluation
Deliverables:
- commentary composer at the daily reading range boundary
- pending review flow with blurred partner commentary until unlock conditions are met
- approve/revision workflow and graceful window for edits
- streak evaluation when all active members finish the range and commentary status is acceptable

### Phase 8 — Chat, Notifications, and AI Assistant
Deliverables:
- paginated Firestore chat on `messages` subcollection with paragraph-chip attachments
- Firebase Cloud Messaging integration
- `DailyReminderWorker` using Room-derived median schedule
- Nudge button writes trigger to Firestore and pushes high-priority notifications
- `AiAssistantRepository` using OpenRouter to generate discussion questions, summarize group chat, and mediate disputes
- final verification artifact confirming builds and tests

## 6. Cross-cutting Validation Strategy
- Run `pytest` in `coread-converter` for conversion service checks
- Run Android release validation with `./gradlew assembleRelease`
- Verify ProGuard/R8 compatibility for Retrofit, Kotlinx Serialization, and Firebase
- Confirm end-to-end app flow from invite link to group dashboard to reading verification

## 7. Task List Summary
1. Build the stateless PDF/EPUB conversion service and health endpoint
2. Secure Firebase group storage and membership rules
3. Create Room + DataStore local data layer
4. Implement auth, groups, and deep-linked invites
5. Build the anti-cheating reading engine and validation rules
6. Add milestone control and pause-request UX
7. Implement commentary review + streak evaluation logic
8. Add general chat, reminders, and AI assistant features
9. Run final release verification and produce the completion report

## 8. Exit Criteria
The project is considered ready when:
- the Render service converts and uploads books reliably
- the Android app can authenticate, invite, read, and sync locally
- all reading verification and streak rules work without cheating
- final build passes release assembly and all relevant tests
- deployment and operational documentation are complete
