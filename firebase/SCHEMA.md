# Firestore schema and authority

groups/{groupId}: name, memberIds (two verified auth UIDs maximum), invitedEmails (normalized lowercase), activeBookId, bookTitle, paragraphCount, currentDayNumber, startParagraphIndex, endParagraphIndex, dailyTargetType (chapters/words/custom), dailyTargetValue, streakCount, lastStreakEvalDate, lastCompletedDay, pauseTokensRemaining, pauseMonth, pauseRequestedBy, pendingRequest, deadline, completed.

All paragraph bounds are zero-based and inclusive. Firestore timestamps are UTC. Daily deadlines are 24-hour intervals. lastStreakEvalDate stores UTC YYYY-MM-DD; lastCompletedDay makes milestone evaluation idempotent. Multiple milestones on one date count once toward the streak, and a calendar gap restarts it at one. pauseMonth is UTC YYYY-MM; monthly tokens reset lazily when a pause is approved.

members/{uid}: email, verifiedThrough (-1 before reading), lastActive, fcmToken. Clients may advance only their own progress within the book and update their own presence/token. Room verification gates the normal app; a modified client can forge dwell evidence, so this is not a hostile-client attestation system.

commentaries/{dayNumber}_{uid}: uid, dayNumber, text, quoteIndex, startParagraphIndex, endParagraphIndex, status, createdAt, updatedAt; reviews add critique, reviewedBy and revisionDeadline. Status is PENDING_REVIEW / APPROVED / REVISION_REQUESTED. The server validates reading/range, prevents self-review, and grants a 12-hour revision window. A pending review is streak-eligible within 24 hours of its latest submission if the original submission was on time.

messages/{autoId}: uid, text, paragraphIndex (-1 for no reference), createdAt. Messages are ordered by createdAt. Rules prevent impersonation and client-supplied timestamps.

nudges/{autoId}: requestedBy, createdAt, wordsRemaining. Server-only audit trigger records; 30-minute group cooldown.

Membership, invitations, milestones, pause approvals, commentaries/reviews and streak evaluation are authoritative FastAPI transactions. Direct client group or commentary writes are denied. Group and Storage reads require membership; partner commentary reads also require completing the daily range.

Default private Supabase Storage (bucket coread-books): groups/{groupId}/books/{bookId}.json contains gzip JSON with metadata, chapters (title, level, startParagraphIndex) and paragraphs (index, text, wordCount). Objects are private. /convert returns the authenticated /groups/{groupId}/books/{bookId} route, not a public token URL.

Current scope: one active book per circle. Create another circle for the next book. Previous/Next controls in Reflect retain access to earlier review cards and open revision windows after a day advances.

Firebase Storage rules are optional legacy-provider tests only. Default deployment uses Firebase Spark and does not deploy or create a Firebase Storage bucket.
