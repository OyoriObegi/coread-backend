# CoRead conversion and group API
Default deployment: Firebase Spark (identity/group data/push), Supabase Free (private books), Render Free (API). See the root FREE_TIER_SETUP.md.

The Android API contract is unchanged: authenticated uploads return an authenticated relative download route. New storage code is in book_storage.py; it supports backend-only Supabase secret keys and an explicitly selected optional Firebase provider. No Supabase SDK dependency is added.

Run python -m pytest -q. Storage adapter tests use HTTP mocks; live Supabase operation requires a project and private bucket.
