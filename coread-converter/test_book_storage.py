import gzip
import json
import httpx
import pytest
from fastapi import HTTPException
from book_storage import SupabaseBucket, book_bucket
import main

PATH = "groups/group-1/books/book-1.json"

def bucket(handler, key="sb_secret_test"):
    return SupabaseBucket(url="https://project.supabase.co", key=key,
                          transport=httpx.MockTransport(handler))

def test_private_upload_uses_server_secret_and_binary_gzip():
    payload = gzip.compress(b'{"paragraphs":[]}')
    seen=[]
    def handler(request):
        seen.append(request)
        assert request.method == "POST"
        assert request.url.path == "/storage/v1/object/coread-books/" + PATH
        assert request.headers["apikey"] == "sb_secret_test"
        assert "authorization" not in request.headers
        assert request.headers["content-type"] == "application/gzip"
        assert request.headers["x-upsert"] == "true"
        assert request.content == payload
        return httpx.Response(200, json={"Key": PATH})
    bucket(handler).blob(PATH).upload_from_string(payload)
    assert len(seen)==1

def test_private_download_never_uses_public_route_and_does_not_fetch_twice():
    requests=[]
    payload=gzip.compress(b'{"metadata":{},"paragraphs":[]}')
    def handler(request):
        requests.append(request)
        assert "/object/authenticated/coread-books/" in request.url.path
        assert "/public/" not in request.url.path
        return httpx.Response(200,content=payload)
    blob=bucket(handler).blob(PATH)
    assert blob.exists() and blob.download_as_bytes()==payload
    assert len(requests)==1

def test_legacy_service_role_jwt_header():
    def handler(request):
        assert request.headers["Authorization"]=="Bearer eyJ.legacy.jwt"
        return httpx.Response(200,content=b'data')
    assert bucket(handler,"eyJ.legacy.jwt").blob(PATH).download_as_bytes()==b'data'

def test_missing_object_is_not_found():
    blob=bucket(lambda r:httpx.Response(404)).blob(PATH)
    assert not blob.exists()
    with pytest.raises(HTTPException) as e: blob.download_as_bytes()
    assert e.value.status_code==404

@pytest.mark.parametrize("status",[401,403,429,500,507])
def test_provider_errors_are_safe_and_never_trigger_a_paid_fallback(status):
    store=bucket(lambda r:httpx.Response(status,text="sb_secret_private_provider_details"))
    with pytest.raises(HTTPException) as e: store.blob(PATH).download_as_bytes()
    assert e.value.status_code==503
    assert "sb_secret" not in e.value.detail

def test_provider_file_limit_is_reported():
    with pytest.raises(HTTPException) as e:
        bucket(lambda r:httpx.Response(413)).blob(PATH).upload_from_string(b'book')
    assert e.value.status_code==413

@pytest.mark.parametrize("path",["../private","groups/g/books/../../secret","groups/g/books/b.json?query=x"])
def test_storage_paths_cannot_escape_group_namespace(path):
    with pytest.raises(HTTPException): bucket(lambda r:None).blob(path)

def test_invalid_configuration_never_sends_key():
    b=SupabaseBucket(url="http://untrusted.invalid",key="sb_secret_test",transport=httpx.MockTransport(lambda r:pytest.fail("must not send")))
    with pytest.raises(HTTPException): b.blob(PATH).download_as_bytes()

def test_publishable_key_rejected():
    with pytest.raises(HTTPException):
        bucket(lambda r:pytest.fail("must not send"),"sb_publishable_test").blob(PATH).download_as_bytes()

def test_storage_outage_does_not_become_misleading_book_404():
    with pytest.raises(HTTPException) as e:
        main.load_book(bucket(lambda r:httpx.Response(503)),"group-1","book-1")
    assert e.value.status_code==503

def test_default_provider_requires_no_firebase_bucket(monkeypatch):
    monkeypatch.delenv("BOOK_STORAGE_PROVIDER",raising=False)
    monkeypatch.delenv("FIREBASE_STORAGE_BUCKET",raising=False)
    assert isinstance(book_bucket(),SupabaseBucket)

def test_unknown_provider_has_no_automatic_fallback(monkeypatch):
    monkeypatch.setenv("BOOK_STORAGE_PROVIDER","paid-provider")
    with pytest.raises(HTTPException) as e: book_bucket()
    assert e.value.status_code==503
