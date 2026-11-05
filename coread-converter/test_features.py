"""Regression tests for creator controls, request recall and book boundaries."""
import gzip
import json
import httpx
import pytest
import main
from book_storage import SupabaseBucket
from test_api import env, post

def manage(env, kind, **kwargs):
    return env.client.post("/groups/g/manage", json={"kind": kind, **kwargs})

def as_bob():
    main.app.dependency_overrides[main.identity] = lambda: {"uid": "bob", "email": "bob@example.com", "email_verified": True}

def test_creator_can_rename_legacy_circle(env):
    assert manage(env,"rename",name="  Evening readers  ").status_code==200
    assert env.g["ownerId"]=="alice" and env.g["name"]=="Evening readers"
    assert manage(env,"rename",name=" ").status_code==422

@pytest.mark.parametrize("kind",["rename","remove_book","delete_circle"])
def test_partner_cannot_manage_circle(env,kind):
    as_bob()
    assert manage(env,kind,name="Taken over",bookId="book").status_code==403
    assert env.g["activeBookId"]=="book"

def test_explicit_owner_is_authoritative(env):
    env.g["ownerId"]="bob"
    assert manage(env,"rename",name="New").status_code==403

def test_remove_resets_both_members_and_retires_old_book(env):
    env.g.update(pendingRequest={"kind":"pause","requestedBy":"bob"},completed=True)
    for uid in ["alice","bob"]: env.db.docs["groups/g/members/"+uid]["verifiedThrough"]=3
    assert manage(env,"remove_book",bookId="book").status_code==200
    assert env.g["activeBookId"] is None and env.g["pendingRequest"] is None
    assert not env.g["completed"] and env.g["currentDayNumber"]==2 and env.g["bookFirstDay"]==2
    assert env.g["retiredBookIds"]==["book"]
    assert "groups/g/books/book.json" not in env.bucket.data
    assert all(env.db.docs["groups/g/members/"+uid]["verifiedThrough"]==-1 for uid in ["alice","bob"])
    assert env.client.get("/groups/g/books/book").status_code==404
    assert post(env,"commentary",dayNumber=1,text="Wrong book",quoteIndex=0).status_code==422

def test_removal_rejects_stale_book_and_import(env):
    assert manage(env,"remove_book",bookId="wrong").status_code==409
    env.g["importingBookId"]="next"
    assert manage(env,"delete_circle").status_code==409

def test_delete_circle_cleans_book_and_all_subcollections(env):
    env.db.docs["groups/g/messages/m"]={"text":"hello"}
    env.db.docs["groups/g/commentaries/1_alice"]={"text":"hello"}
    env.db.docs["groups/g/nudges/n"]={}
    env.db.docs["groups/other"]={"memberIds":["elsewhere"]}
    assert manage(env,"delete_circle").status_code==200
    assert not any(p=="groups/g" or p.startswith("groups/g/") for p in env.db.docs)
    assert "groups/other" in env.db.docs
    assert not env.bucket.data

def test_failed_storage_delete_can_be_retried_and_blocks_other_actions(env,monkeypatch):
    from test_api import Blob
    original=Blob.delete
    def fail(self): raise main.HTTPException(503,"Unavailable")
    monkeypatch.setattr(Blob,"delete",fail)
    assert manage(env,"remove_book",bookId="book").status_code==503
    assert env.g["activeBookId"]=="book"
    assert post(env,"pause").status_code==409
    assert manage(env,"rename",name="No").status_code==409
    monkeypatch.setattr(Blob,"delete",original)
    assert manage(env,"remove_book",bookId="book").status_code==200

def test_cancel_preserves_tokens_deadline_and_rejects_late_partner(env):
    deadline=env.g["deadline"]
    assert post(env,"pause").status_code==200
    request=env.g["pendingRequest"]["id"]
    assert post(env,"cancel",target=request).status_code==200
    assert env.g["pendingRequest"] is None
    assert env.g["pauseTokensRemaining"]==3 and env.g["deadline"]==deadline
    as_bob()
    assert post(env,"accept",target=request).status_code==403

def test_partner_cannot_cancel_and_stale_response_cannot_accept_new_request(env):
    assert post(env,"pace",mode="custom",value=3).status_code==200
    first=env.g["pendingRequest"]["id"]
    as_bob()
    assert post(env,"cancel",target=first).status_code==403
    assert post(env,"accept",target="stale").status_code==409
    assert env.g["endParagraphIndex"]==1
    assert post(env,"accept",target=first).status_code==200
    assert env.g["endParagraphIndex"]==2

def test_stale_cancel_cannot_remove_new_request(env):
    assert post(env,"pause").status_code==200
    first=env.g["pendingRequest"]["id"]
    assert post(env,"cancel",target=first).status_code==200
    assert post(env,"pause").status_code==200
    assert post(env,"cancel",target=first).status_code==409
    assert env.g["pendingRequest"] is not None

def test_reflection_recall_only_before_review(env):
    env.db.docs["groups/g/members/alice"]["verifiedThrough"]=1
    assert post(env,"commentary",text="Thought",quoteIndex=0).status_code==200
    assert post(env,"withdraw_reflection").status_code==200
    assert "groups/g/commentaries/1_alice" not in env.db.docs
    assert post(env,"commentary",text="Changed thought",quoteIndex=0).status_code==200
    env.db.docs["groups/g/commentaries/1_alice"]["reviewedBy"]="bob"
    assert post(env,"withdraw_reflection").status_code==409

def test_supabase_delete_uses_storage_api_and_exact_path():
    def handler(request):
        assert request.method=="DELETE"
        assert request.url.path=="/storage/v1/object/coread-books"
        assert json.loads(request.content)=={"prefixes":["groups/g/books/book.json"]}
        assert request.headers["apikey"]=="sb_secret_test"
        return httpx.Response(200,json=[])
    SupabaseBucket(url="https://example.supabase.co",key="sb_secret_test",transport=httpx.MockTransport(handler)).blob("groups/g/books/book.json").delete()

def book_with(*texts, chapters=()):
    return {"paragraphs":[{"index":i,"text":text,"wordCount":len(text.split())} for i,text in enumerate(texts)],"chapters":list(chapters)}

def test_frontmatter_start_uses_chapter_markers():
    book=book_with("Title","Contents Chapter One .... 5","Preface","Chapter One","Story",chapters=[{"title":"Preface","startParagraphIndex":2},{"title":"Chapter One","startParagraphIndex":3}])
    assert main.content_start(book)==3
    assert len(book["paragraphs"])==5

@pytest.mark.parametrize("heading",["Chapter 1","CHAPTER I","Chapter One: The beginning","Part I"])
def test_standalone_first_chapter(heading):
    assert main.content_start(book_with("Copyright","Contents",heading,"Story"))==2

def test_unknown_frontmatter_is_not_guessed_from_prose():
    assert main.content_start(book_with("Title","In chapter one we discuss reading.","A story begins."))==0
    assert main.content_start(book_with("Contents","Chapter One .... 9","Preface"))==0

def test_pdf_conversion_skips_preface_for_daily_goal(env):
    import pymupdf as fitz
    env.g["activeBookId"]=None
    doc=fitz.open()
    for text in ["Title page","Preface text","Chapter One","Story body"]:
        page=doc.new_page();page.insert_text((50,100),text)
    doc.set_toc([[1,"Preface",2],[1,"Chapter One",3]])
    response=env.client.post("/convert",data={"groupId":"g","bookId":"new"},files={"file":("book.pdf",doc.tobytes())})
    assert response.status_code==200,response.text
    assert env.g["startParagraphIndex"]==2
    assert env.g["endParagraphIndex"]==3
    assert env.db.docs["groups/g/members/bob"]["verifiedThrough"]==1

def test_only_creator_can_import(env):
    import pymupdf as fitz
    env.g["activeBookId"]=None
    as_bob()
    doc=fitz.open();page=doc.new_page();page.insert_text((50,100),"Body")
    assert env.client.post("/convert",data={"groupId":"g","bookId":"new"},files={"file":("book.pdf",doc.tobytes())}).status_code==403

def test_retired_book_id_cannot_alias_cached_reader_content(env):
    import pymupdf as fitz
    env.g.update(activeBookId=None,retiredBookIds=["book"])
    doc=fitz.open();page=doc.new_page();page.insert_text((50,100),"Body")
    assert env.client.post("/convert",data={"groupId":"g","bookId":"book"},files={"file":("book.pdf",doc.tobytes())}).status_code==409

def test_existing_book_can_skip_preliminaries_without_losing_read_progress(env):
    book=book_with("Copyright","Preface","Chapter One","Story",chapters=[{"title":"Chapter One","startParagraphIndex":2}])
    env.bucket.data["groups/g/books/book.json"]=gzip.compress(json.dumps(book).encode())
    env.db.docs["groups/g/members/alice"]["verifiedThrough"]=3
    assert manage(env,"skip_preliminaries",bookId="book").status_code==200
    assert env.g["startParagraphIndex"]==2 and env.g["endParagraphIndex"]==3
    assert env.db.docs["groups/g/members/alice"]["verifiedThrough"]==3
    assert env.db.docs["groups/g/members/bob"]["verifiedThrough"]==1

def test_skip_preliminaries_cannot_change_a_submitted_or_later_day(env):
    book=book_with("Preface","Chapter One","Story",chapters=[{"title":"Chapter One","startParagraphIndex":1}])
    env.bucket.data["groups/g/books/book.json"]=gzip.compress(json.dumps(book).encode())
    env.db.docs["groups/g/commentaries/1_alice"]={"status":"PENDING_REVIEW"}
    assert manage(env,"skip_preliminaries",bookId="book").status_code==409
    env.db.docs.pop("groups/g/commentaries/1_alice")
    env.g["currentDayNumber"]=2
    assert manage(env,"skip_preliminaries",bookId="book").status_code==409

def test_unknown_start_is_reported_instead_of_discarding_pages(env):
    assert manage(env,"skip_preliminaries",bookId="book").status_code==422
    assert env.g["startParagraphIndex"]==0

def test_contents_entries_extracted_as_headings_do_not_become_daily_starts():
    book=book_with("Title","Chapter One .... 9","Preface","Chapter One","Story",chapters=[{"title":"Chapter One .... 9","startParagraphIndex":1},{"title":"Chapter One","startParagraphIndex":3}])
    assert main.content_start(book)==3
    book["chapters"]=[{"title":"Chapter One 9","startParagraphIndex":1}]
    assert main.content_start(book)==3

def test_import_records_active_book_metadata_and_initial_progress(env):
    import pymupdf as fitz
    env.g['activeBookId']=None
    doc=fitz.open()
    for text in ['Preface','Chapter One','Body']:
        page=doc.new_page();page.insert_text((50,100),text)
    doc.set_toc([[1,'Chapter One',2]])
    response=env.client.post('/convert',data={'groupId':'g','bookId':'next'},files={'file':('A new story.pdf',doc.tobytes())})
    assert response.status_code==200,response.text
    history=env.db.docs['groups/g/readingHistory/next']
    assert history['bookId']=='next' and history['title']=='A new story'
    assert history['paragraphCount']==3 and history['readingStartParagraphIndex']==1
    assert history['status']=='active' and not history['completed']
    assert history['startedAt'] and history['removedAt'] is None
    assert history['verifiedThroughByUid']=={'alice':0,'bob':0}
    assert env.g['currentDayNumber']==1

def test_removing_unfinished_legacy_book_archives_each_readers_actual_progress(env):
    env.g.update(bookTitle='Old story',currentDayNumber=5,bookFirstDay=2,contentStartParagraphIndex=1,streakCount=3,bestStreak=7)
    env.db.docs['groups/g/members/alice']['verifiedThrough']=2
    env.db.docs['groups/g/members/bob']['verifiedThrough']=1
    assert manage(env,'remove_book',bookId='book').status_code==200
    history=env.db.docs['groups/g/readingHistory/book']
    assert history['title']=='Old story' and history['status']=='stopped'
    assert history['verifiedThroughByUid']=={'alice':2,'bob':1}
    assert history['paragraphCount']==4 and history['readingStartParagraphIndex']==1
    assert history['removedAt'] and history['startedAt'] is None
    assert not history['completed']
    assert 'text' not in history and 'paragraphs' not in history
    assert env.g['currentDayNumber']==6 and env.g['bookFirstDay']==6
    assert env.g['streakCount']==3 and env.g['bestStreak']==7
    assert not env.bucket.data
    assert all(env.db.docs['groups/g/members/'+uid]['verifiedThrough']==-1 for uid in ['alice','bob'])
    assert manage(env,'remove_book',bookId='book').status_code==409
    assert manage(env,'remove_book',bookId='').status_code==409
    assert env.g['currentDayNumber']==6

def _ready_to_complete(env):
    env.g.update(startParagraphIndex=2,endParagraphIndex=3)
    for uid in ['alice','bob']:
        env.db.docs['groups/g/members/'+uid]['verifiedThrough']=3
        env.db.docs['groups/g/commentaries/1_'+uid]={'status':'PENDING_REVIEW','createdAt':env.now}

def test_finishing_book_marks_history_complete_and_preserves_it_after_removal(env):
    _ready_to_complete(env)
    env.g.update(bookTitle='Finished story',bestStreak=7,streakCount=3,lastStreakEvalDate=(env.now.date()-main.timedelta(days=1)).isoformat())
    env.db.docs['groups/g/readingHistory/book']={'startedAt':env.now-main.timedelta(days=5)}
    assert post(env,'evaluate').status_code==200
    history=dict(env.db.docs['groups/g/readingHistory/book'])
    assert history['status']=='completed' and history['completed']
    assert history['finishedAt'] and history['removedAt'] is None
    assert history['verifiedThroughByUid']=={'alice':3,'bob':3}
    assert env.g['bestStreak']==7 and env.g['streakCount']==4
    assert manage(env,'remove_book',bookId='book').status_code==200
    archived=env.db.docs['groups/g/readingHistory/book']
    assert archived['status']=='completed' and archived['completed']
    assert archived['finishedAt']==history['finishedAt'] and archived['startedAt']==history['startedAt']
    assert archived['removedAt']

def test_best_streak_grows_and_never_forgets_prior_legacy_streak(env):
    _ready_to_complete(env)
    env.g.update(streakCount=6,lastStreakEvalDate=(env.now.date()-main.timedelta(days=1)).isoformat())
    assert post(env,'evaluate').status_code==200
    assert env.g['streakCount']==7 and env.g['bestStreak']==7
    env.g.update(completed=False,lastCompletedDay=0,lastStreakEvalDate='2000-01-01')
    assert post(env,'evaluate').status_code==200
    assert env.g['streakCount']==1 and env.g['bestStreak']==7

def test_circle_delete_removes_history_and_leaves_other_circles_alone(env):
    env.db.docs['groups/g/readingHistory/old']={'bookId':'old','status':'stopped'}
    env.db.docs['groups/other/readingHistory/old']={'bookId':'old','status':'stopped'}
    assert manage(env,'delete_circle').status_code==200
    assert 'groups/g/readingHistory/old' not in env.db.docs
    assert 'groups/other/readingHistory/old' in env.db.docs

def test_history_start_tracks_skipped_preliminaries(env):
    book=book_with('Title','Preface','Chapter One','Story',chapters=[{'title':'Chapter One','startParagraphIndex':2}])
    env.bucket.data['groups/g/books/book.json']=gzip.compress(json.dumps(book).encode())
    assert manage(env,'skip_preliminaries',bookId='book').status_code==200
    history=env.db.docs['groups/g/readingHistory/book']
    assert history['readingStartParagraphIndex']==2
    assert history['verifiedThroughByUid']=={'alice':1,'bob':1}


