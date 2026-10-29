"""Server-only private object storage. Default provider works on Supabase Free."""
import os
import re
from urllib.parse import quote, urlsplit

import httpx
from fastapi import HTTPException

class SupabaseBucket:
    def __init__(self, *, url=None, key=None, bucket=None, transport=None):
        self.url = (url if url is not None else os.getenv("SUPABASE_URL", "")).rstrip("/")
        self.key = key if key is not None else (os.getenv("SUPABASE_SECRET_KEY") or os.getenv("SUPABASE_SERVICE_ROLE_KEY", ""))
        self.name = bucket if bucket is not None else os.getenv("SUPABASE_STORAGE_BUCKET", "coread-books")
        self.transport = transport

    def blob(self, path):
        if not re.fullmatch(r"groups/[A-Za-z0-9_-]{1,128}/books/[A-Za-z0-9_-]{1,128}\.json", path):
            raise HTTPException(422, "Invalid book storage path")
        return SupabaseBlob(self, path)

    def request(self, method, path, **kwargs):
        url = urlsplit(self.url)
        if url.scheme != "https" or not url.netloc or url.path not in {"", "/"} or url.username or url.query or url.fragment:
            raise HTTPException(503, "Configure SUPABASE_URL on the server")
        if not (self.key.startswith("sb_secret_") or self.key.startswith("eyJ")):
            raise HTTPException(503, "Configure a server-only Supabase secret/service-role key")
        if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,99}", self.name):
            raise HTTPException(503, "Invalid server storage bucket configuration")
        headers = {"apikey": self.key}
        # New secret keys use apikey, never Bearer. Legacy service_role is a JWT.
        if self.key.startswith("eyJ"):
            headers["Authorization"] = "Bearer " + self.key
        headers.update(kwargs.pop("headers", {}))
        endpoint = self.url + "/storage/v1/object/"
        if method == "GET":
            endpoint += "authenticated/"
        endpoint += quote(self.name, safe="")
        if method != "DELETE":
            endpoint += "/" + quote(path, safe="/")
        try:
            with httpx.Client(timeout=90, transport=self.transport, follow_redirects=False) as client:
                response = client.request(method, endpoint, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            raise HTTPException(503, "Book storage unavailable; try again later") from exc
        if response.status_code == 404:
            raise HTTPException(404, "Book not found")
        if response.status_code == 413:
            raise HTTPException(413, "Converted book exceeds the storage upload limit")
        if not response.is_success:
            # Never expose provider response bodies, keys, or internal URLs.
            raise HTTPException(503, "Book storage unavailable or its free quota has been reached")
        return response

class SupabaseBlob:
    def __init__(self, bucket, path):
        self.bucket, self.path, self.cached = bucket, path, None

    def upload_from_string(self, data, content_type="application/gzip"):
        self.bucket.request("POST", self.path, content=data,
                            headers={"Content-Type": content_type, "x-upsert": "true"})
        self.cached = None

    def delete(self):
        self.bucket.request("DELETE", self.path, json={"prefixes": [self.path]})
        self.cached = None

    def download_as_bytes(self):
        if self.cached is None:
            self.cached = self.bucket.request("GET", self.path).content
        return self.cached

    def exists(self):
        try:
            self.download_as_bytes()
            return True
        except HTTPException as exc:
            if exc.status_code == 404:
                return False
            raise

def book_bucket():
    provider = os.getenv("BOOK_STORAGE_PROVIDER", "supabase").lower()
    if provider == "supabase":
        return SupabaseBucket()
    if provider == "firebase":
        from firebase_admin import storage
        name = os.getenv("FIREBASE_STORAGE_BUCKET")
        if not name:
            raise HTTPException(503, "Configure FIREBASE_STORAGE_BUCKET for the optional Firebase provider")
        return storage.bucket(name=name)
    raise HTTPException(503, "Unsupported book storage provider")
