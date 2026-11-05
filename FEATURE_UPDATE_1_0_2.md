> Latest APK: use CoRead 1.0.3. It fixes progress-sync permission errors and can be installed now over the existing app. The backend steps below remain necessary for the circle-management features.

# CoRead 1.0.2 update — 29 October 2026

The Android app and backend changes are prepared. Firestore rules have already been tested and deployed to coread-9bc50. No new environment variables or paid plans are needed.

## 1. Ask Copilot to push the backend update
Copy this request to the Copilot session that successfully pushed the first backend:

> Update the existing repository https://github.com/OyoriObegi/coread-backend from the prepared folder C:\Users\ASC\Documents\Codex\2026-10-28\i-x20\outputs\coread-backend-public. Clone or fetch the current repository first, preserve its Git history, copy only these eight files: .dockerignore, .gitignore, book_storage.py, Dockerfile, main.py, README.md, render.yaml, requirements.txt. Review the diff, commit the changes with message "Add circle management, request recall and chapter-aware reading goals", and push to main. Do not copy credentials, .env files, Firebase JSON files, book files, logs, tests or any other project files. Report the resulting commit and whether the push succeeded.

The prepared files are also in CoRead-backend-public.zip. This update changes main.py, book_storage.py and README.md. A matching local commit is prepared in the work/coread-backend-review checkout.

## 2. Deploy the updated backend on Render
1. Open https://dashboard.render.com and select the CoRead backend service.
2. Choose Manual Deploy > Deploy latest commit.
3. Wait for the service to show Live. Keep the existing environment variables and Free instance.
4. Open https://coread-backend.onrender.com/health. It should return {"status":"ok"}.
5. Confirm https://coread-backend.onrender.com/openapi.json contains /groups/{group_id}/manage. This distinguishes the new server from the previous deployment.

The first request can take time while the free service wakes. No new service or paid plan is required.

## 3. Install the updated app
Install CoRead-1.0.2.apk over the existing CoRead installation on both partners' phones. Do not uninstall first; updating keeps local reading data. This test build uses the same development certificate as CoRead 1.0.1.

## 4. Test the changes
Use a disposable circle/book when testing deletion.

- Reading position: scroll partway through a paragraph, switch tabs and return. Then close/reopen the app. The reader should return to that paragraph and offset. Saved positions are separate for each account, circle and book on this device.
- Circle name: the creator uses Home > Manage your circle > Edit circle name. The partner should see the new name and should not see creator controls.
- Request recall: request a pause or pace change, then choose Cancel request before the partner responds. It should disappear for both readers without spending a pause token or changing the goal/deadline.
- Reflection recall: submit a reflection, then choose Recall reflection before the partner reviews it. A reviewed reflection or completed day cannot be recalled.
- Preliminary pages: new imports begin daily goals at a detected first chapter. Preliminary pages remain readable. For an existing book, the creator can use Start daily goals at Chapter 1 before either partner submits the first reflection. If the first chapter cannot be identified, the app reports that and retains the current start.
- Book replacement: creator chooses Remove or replace book and confirms. This deletes the server book and resets both readers' progress. Import a replacement with Import PDF or EPUB. Shared chat remains; old reflections are kept on earlier circle days and cannot satisfy the new book's goals.
- Circle deletion: creator chooses Delete circle and confirms. The circle disappears for both partners, and its server book, members, messages, reflections and nudges are deleted.

If removal or deletion fails because storage is unavailable, retry the same operation. The server blocks other circle changes until that operation finishes.

## Remaining live checks
Google sign-in worked on the previous build. Email-link sign-in still needs a device test after reinstalling. Private Supabase upload/download and the new two-person flows require signed-in phone testing; public health checks cannot establish those.

Automatic chapter detection uses recognizable first-chapter/part markers. Unusual formatting or unnumbered chapter names may need a later manual reading-start feature. The app preserves the book text when uncertain.

