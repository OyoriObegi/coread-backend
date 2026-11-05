# CoRead 1.0.4

Built on 29 October 2026. Signed APK: 3,450,176 bytes. SHA-256: 5f9d00a1477fce636bf0186532738c9278e764d9af115790355f9a671c527777.

The bookshelf tab shows the current and previous books in the selected circle. Each card shows your individual reading progress, completion status and move-on date. The server records book metadata and both partners' verified positions; deleted book text is not retained in the history. A book's shared goal is marked complete only when both partners finish the last range.

The existing circle day counter continues across replacement books. Streaks are more prominent on Home and the bookshelf, with permanent circle badges at 3, 7, 14 and 30 days. The card shows progress toward the next badge and keeps the best streak when a new book begins.

Settings no longer exposes the internal conversion-service URL. Sign-in, sign-out, the service endpoint and reading verification continue to work.

The Firestore rules have been deployed. The Android release build and size check succeeded. Firebase project identity, link hosts, internal service address and v2/v3 signing were checked in the package. The previous APK used the same signing key so this installs over the current copy and keeps local reading data.

On the bookshelf, a current book and history records created by this update are shown. Books that were already removed before the history feature existed have no saved title or progress record, so they cannot be reconstructed.
