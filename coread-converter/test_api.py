
import gzip
import json
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
import pytest
import pymupdf as fitz
from fastapi.testclient import TestClient
import main

class Snapshot:
    def __init__(self,ref): self.reference=ref;self.id=ref.id
    def to_dict(self): return self.reference.db.docs.get(self.reference.path)
class Ref:
    def __init__(self,db,path): self.db=db;self.path=path;self.id=path.rsplit('/',1)[-1]
    def get(self,transaction=None):
        if transaction and transaction.wrote: raise AssertionError('Firestore transactions must read before writing')
        return Snapshot(self)
    def set(self,data): self.db.docs[self.path]=dict(data)
    def update(self,data): self.db.docs[self.path].update(data)
    def delete(self): self.db.docs.pop(self.path,None)
    def collection(self,name): return Collection(self.db,self.path+'/'+name)
class Collection:
    def __init__(self,db,path): self.db=db;self.path=path
    def document(self,name='generated'): return Ref(self.db,self.path+'/'+name)
    def stream(self): return [Snapshot(Ref(self.db,p)) for p in self.db.docs if p.rsplit('/',1)[0]==self.path]
class Tx:
    def __init__(self): self.wrote=False
    def set(self,ref,data): self.wrote=True;ref.set(data)
    def update(self,ref,data): self.wrote=True;ref.update(data)
    def delete(self,ref): self.wrote=True;ref.delete()
class DB:
    def __init__(self): self.docs={}
    def collection(self,name): return Collection(self,name)
    def transaction(self): return Tx()
    def recursive_delete(self,collection):
        for path in list(self.docs):
            if path.startswith(collection.path+"/"): del self.docs[path]
class Blob:
    def __init__(self,bucket,path): self.bucket=bucket;self.path=path
    def delete(self): self.bucket.data.pop(self.path,None)
    def exists(self): return self.path in self.bucket.data
    def upload_from_string(self,data,**kwargs): self.bucket.data[self.path]=data
    def download_as_bytes(self): return self.bucket.data[self.path]
class Bucket:
    def __init__(self): self.data={}
    def blob(self,path): return Blob(self,path)
@pytest.fixture
def env(monkeypatch):
    from google.cloud import firestore
    db=DB();bucket=Bucket();now=datetime.now(timezone.utc)
    g={'name':'Pair','memberIds':['alice','bob'],'invitedEmails':[],'activeBookId':'book','currentDayNumber':1,'startParagraphIndex':0,'endParagraphIndex':1,'paragraphCount':4,'dailyTargetType':'custom','dailyTargetValue':2,'streakCount':0,'lastStreakEvalDate':'','pauseTokensRemaining':3,'pauseMonth':now.strftime('%Y-%m'),'pendingRequest':None,'deadline':now+timedelta(hours=1)}
    db.docs['groups/g']=g
    for uid in ['alice','bob']: db.docs['groups/g/members/'+uid]={'email':uid+'@example.com','verifiedThrough':-1}
    book={'metadata':{},'paragraphs':[{'index':i,'text':'Word '+str(i),'wordCount':2} for i in range(4)],'chapters':[]}
    bucket.data['groups/g/books/book.json']=gzip.compress(json.dumps(book).encode())
    monkeypatch.setattr(main,'services',lambda:(db,bucket))
    monkeypatch.setattr(firestore,'transactional',lambda fn:fn)
    monkeypatch.setattr(main,'notify',lambda *a:None)
    main.app.dependency_overrides[main.identity]=lambda:{'uid':'alice','email':'alice@example.com','email_verified':True}
    yield SimpleNamespace(db=db,bucket=bucket,g=g,now=now,client=TestClient(main.app))
    main.app.dependency_overrides.clear()
def post(env,kind,**kwargs): return env.client.post('/groups/g/actions',json={'kind':kind,**kwargs})
def test_private_download(env):
    response=env.client.get('/groups/g/books/book')
    assert response.status_code==200
    assert len(json.loads(gzip.decompress(response.content))['paragraphs'])==4
def test_cross_group_download_denied(env):
    env.g['memberIds']=['charlie']
    assert env.client.get('/groups/g/books/book').status_code==403
def test_upload_packages_private_gzip(env):
    env.g['activeBookId']=None
    doc=fitz.open();page=doc.new_page();page.insert_text((50,100),'Readable book body.')
    response=env.client.post('/convert',data={'groupId':'g','bookId':'new'},files={'file':('book.pdf',doc.tobytes(),'application/pdf')})
    assert response.status_code==200
    assert response.json()['downloadUrl']=='/groups/g/books/new'
    assert env.g['activeBookId']=='new'
    assert json.loads(gzip.decompress(env.bucket.data['groups/g/books/new.json']))['paragraphs'][0]['text']=='Readable book body.'
def test_existing_book_cannot_be_overwritten(env):
    doc=fitz.open();page=doc.new_page();page.insert_text((50,100),'Content')
    response=env.client.post('/convert',data={'groupId':'g','bookId':'book'},files={'file':('b.pdf',doc.tobytes())})
    assert response.status_code==409

def test_preview_requires_confirmation_and_uses_selected_start(env):
    env.g['activeBookId'] = None
    with fitz.open() as doc:
        page = doc.new_page()
        page.insert_text((50, 100), "Copyright and publication details.")
        page = doc.new_page()
        page.insert_text((50, 100), "Chapter One", fontsize=24)
        page.insert_text((50, 160), "The story begins.")
        doc.set_toc([[1, "Chapter One", 2]])
        raw = doc.tobytes()
    data = {'groupId': 'g', 'bookId': 'new'}
    files = {'file': ('book.pdf', raw, 'application/pdf')}
    preview = env.client.post('/convert/preview', data=data, files=files)
    assert preview.status_code == 200
    assert preview.json()['suggestedStart'] == 1
    assert preview.json()['choices'][0]['sample'] == ["Chapter One", "The story begins."]
    assert env.g['activeBookId'] is None
    assert 'groups/g/books/new.json' not in env.bucket.data

    selection = dict(data, startParagraphIndex=2, fingerprint='wrong')
    assert env.client.post('/convert/confirm', data=selection, files=files).status_code == 409
    assert env.g['activeBookId'] is None
    selection['fingerprint'] = preview.json()['fingerprint']
    assert env.client.post('/convert/confirm', data=selection, files=files).status_code == 200
    assert env.g['contentStartParagraphIndex'] == env.g['startParagraphIndex'] == 2
    assert env.db.docs['groups/g/members/bob']['verifiedThrough'] == 1
    saved = json.loads(gzip.decompress(env.bucket.data['groups/g/books/new.json']))
    assert saved['paragraphs'][0]['text'] == "Copyright and publication details."
def test_invited_verified_user_joins(env):
    env.g['memberIds']=['bob'];env.g['invitedEmails']=['alice@example.com']
    assert env.client.post('/groups/g/join').status_code==200
    assert env.g['memberIds']==['bob','alice'] and not env.g['invitedEmails']
def test_uninvited_user_cannot_join(env):
    env.g['memberIds']=['bob']
    assert env.client.post('/groups/g/join').status_code==403
def test_cannot_approve_own_pause(env):
    assert post(env,'pause').status_code==200
    assert post(env,'accept').status_code==403
    assert env.g['pauseTokensRemaining']==3
def test_partner_pause_is_atomic_and_once(env):
    env.g['pendingRequest']={'kind':'pause','requestedBy':'bob'}
    deadline=env.g['deadline']
    assert post(env,'accept').status_code==200
    assert env.g['deadline']==deadline+timedelta(days=1)
    assert env.g['pauseTokensRemaining']==2
    assert post(env,'accept').status_code==403
def test_monthly_pause_tokens_reset(env):
    env.g.update(pauseTokensRemaining=0,pauseMonth='2000-01',pendingRequest={'kind':'pause','requestedBy':'bob'})
    assert post(env,'accept').status_code==200
    assert env.g['pauseTokensRemaining']==2
def test_empty_tokens_reject_pause(env):
    env.g.update(pauseTokensRemaining=0,pendingRequest={'kind':'pause','requestedBy':'bob'})
    assert post(env,'accept').status_code==409
def test_pace_requires_partner(env):
    assert post(env,'pace',mode='words',value=3).status_code==200
    assert env.g['endParagraphIndex']==1
    assert env.g['pendingRequest']['end']==1
    assert post(env,'accept').status_code==403
def test_commentary_gated_by_reading_and_quote(env):
    assert post(env,'commentary',text='A reflection',quoteIndex=0).status_code==422
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    assert post(env,'commentary',text='A reflection',quoteIndex=2).status_code==422
    assert post(env,'commentary',text='A reflection',quoteIndex=0).status_code==200
    assert post(env,'commentary',text='Overwrite',quoteIndex=0).status_code==409
def test_cannot_review_own_commentary(env):
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    assert post(env,'commentary',text='A reflection',quoteIndex=0).status_code==200
    assert post(env,'approve',target='alice').status_code==403
def test_revision_requires_reason_and_has_grace(env):
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    env.db.docs['groups/g/commentaries/1_bob']={'uid':'bob','status':'PENDING_REVIEW','text':'Reflection','createdAt':env.now}
    assert post(env,'revise',target='bob').status_code==422
    assert post(env,'revise',target='bob',text='Could you explain the connection?').status_code==200
    entry=env.db.docs['groups/g/commentaries/1_bob']
    assert entry['status']=='REVISION_REQUESTED'
    assert entry['revisionDeadline']>env.now+timedelta(hours=11)
def test_streak_requires_both_partners_and_is_idempotent(env):
    assert post(env,'evaluate').status_code==409
    for uid in ['alice','bob']:
        env.db.docs['groups/g/members/'+uid]['verifiedThrough']=1
        env.db.docs['groups/g/commentaries/1_'+uid]={'status':'PENDING_REVIEW','createdAt':env.now}
    assert post(env,'evaluate').status_code==200
    assert env.g['streakCount']==1 and env.g['currentDayNumber']==2
    assert post(env,'evaluate').status_code==409
    assert env.g['streakCount']==1
def test_nudge_throttle(env):
    assert env.client.post('/groups/g/nudge').status_code==200
    assert env.client.post('/groups/g/nudge').status_code==429

def test_invite_page_rejects_html_identifier(env):
    assert env.client.get('/invite',params={'groupId':'<script>'}).status_code==422
    response=env.client.get('/invite',params={'groupId':'group_123'})
    assert response.status_code==200 and 'group_123' in response.text

def test_association_file_configurable(monkeypatch):
    monkeypatch.setenv('ANDROID_CERT_SHA256','AA:BB,CC:DD')
    assert main.asset_links()[0]['target']['sha256_cert_fingerprints']==['AA:BB','CC:DD']

def test_previous_day_review_does_not_require_reading_new_day(env):
    env.g.update(currentDayNumber=2,startParagraphIndex=2,endParagraphIndex=3)
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    env.db.docs['groups/g/commentaries/1_bob']={'uid':'bob','status':'PENDING_REVIEW','createdAt':env.now,'endParagraphIndex':1}
    assert post(env,'approve',target='bob',dayNumber=1).status_code==200

def test_previous_day_revision_preserves_original_submission(env):
    env.g.update(currentDayNumber=2,startParagraphIndex=2,endParagraphIndex=3)
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    created=env.now-timedelta(hours=2)
    env.db.docs['groups/g/commentaries/1_alice']={'uid':'alice','status':'REVISION_REQUESTED','createdAt':created,'revisionDeadline':env.now+timedelta(hours=10),'startParagraphIndex':0,'endParagraphIndex':1}
    assert post(env,'commentary',dayNumber=1,text='Revised connection',quoteIndex=0).status_code==200
    entry=env.db.docs['groups/g/commentaries/1_alice']
    assert entry['createdAt']==created and entry['endParagraphIndex']==1

def test_cannot_create_new_reflection_for_closed_day(env):
    env.g.update(currentDayNumber=2,startParagraphIndex=2,endParagraphIndex=3)
    env.db.docs['groups/g/members/alice']['verifiedThrough']=3
    assert post(env,'commentary',dayNumber=1,text='Late entry',quoteIndex=0).status_code==409

def test_ai_reports_missing_configuration(env,monkeypatch):
    monkeypatch.delenv('OPENROUTER_API_KEY',raising=False)
    assert env.client.post('/groups/g/ai',json={'kind':'questions'}).status_code==503

def test_ai_questions_require_reading_completion(env,monkeypatch):
    monkeypatch.setenv('OPENROUTER_API_KEY','test-only-key')
    assert env.client.post('/groups/g/ai',json={'kind':'questions'}).status_code==403

def test_mediation_uses_selected_previous_day_range(env,monkeypatch):
    import httpx
    monkeypatch.setenv('OPENROUTER_API_KEY','test-only-key')
    monkeypatch.setattr(Collection,'where',lambda self,**kwargs:self,raising=False)
    env.g.update(currentDayNumber=2,startParagraphIndex=2,endParagraphIndex=3)
    env.db.docs['groups/g/members/alice']['verifiedThrough']=1
    env.db.docs['groups/g/commentaries/1_alice']={'text':'Thought','critique':'Please explain','startParagraphIndex':0,'endParagraphIndex':1}
    captured={}
    def reply(*args,**kwargs):
        captured.update(kwargs['json'])
        return SimpleNamespace(raise_for_status=lambda:None,json=lambda:{'choices':[{'message':{'content':'Try a clearer example.'}}]})
    monkeypatch.setattr(httpx,'post',reply)
    response=env.client.post('/groups/g/ai',json={'kind':'mediate','dayNumber':1})
    assert response.status_code==200
    assert 'Word 0' in captured['messages'][1]['content']
    assert 'Word 2' not in captured['messages'][1]['content']

def test_multiple_milestones_same_date_count_one_streak_day(env):
    for uid in ['alice','bob']:
        env.db.docs['groups/g/members/'+uid]['verifiedThrough']=3
        for day in [1,2]:
            env.db.docs['groups/g/commentaries/'+str(day)+'_'+uid]={'status':'APPROVED','createdAt':env.now}
    assert post(env,'evaluate').status_code==200
    assert post(env,'evaluate').status_code==200
    assert env.g['streakCount']==1
    assert env.g['lastCompletedDay']==2
    assert post(env,'evaluate').status_code==409

def test_streak_resets_after_calendar_gap(env):
    env.g.update(streakCount=8,lastStreakEvalDate=(env.now-timedelta(days=3)).date().isoformat())
    for uid in ['alice','bob']:
        env.db.docs['groups/g/members/'+uid]['verifiedThrough']=1
        env.db.docs['groups/g/commentaries/1_'+uid]={'status':'APPROVED','createdAt':env.now}
    assert post(env,'evaluate').status_code==200
    assert env.g['streakCount']==1 and env.g['lastStreakEvalDate']==env.now.date().isoformat()
