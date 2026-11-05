from datetime import datetime, timedelta, timezone
import io
import pytest
import pymupdf as fitz
from ebooklib import epub
from fastapi.testclient import TestClient
import main

client = TestClient(main.app)

def test_health():
    assert client.get('/health').json() == {'status': 'ok'}

def test_authentication_required():
    assert client.post('/convert', data={'groupId':'g','bookId':'b'}, files={'file':('b.pdf',b'no')}).status_code == 401

def test_scanned_pdf_rejected():
    doc = fitz.open(); doc.new_page()
    with pytest.raises(main.HTTPException, match='Scanned image'):
        main._paragraphs_from_pdf(doc.tobytes())

def test_invalid_pdf_rejected():
    with pytest.raises(main.HTTPException, match='Invalid PDF'):
        main._paragraphs_from_pdf(b'not pdf')

def test_pdf_heading_and_word_counts():
    doc=fitz.open(); p=doc.new_page()
    p.insert_text((50,100),'Chapter One',fontsize=24)
    p.insert_text((50,150),'This is a paragraph with seven words.',fontsize=12)
    p.insert_text((50,180),'A second paragraph.',fontsize=12)
    book=main._paragraphs_from_pdf(doc.tobytes())
    assert book['chapters'][0]['title']=='Chapter One'
    assert book['chapters'][0]['startParagraphIndex']==0
    assert all(p['wordCount']==len(p['text'].split()) for p in book['paragraphs'])
    assert [p['index'] for p in book['paragraphs']]==list(range(3))

def test_pdf_toc_links_to_paragraph():
    doc=fitz.open()
    for i in range(2):
        p=doc.new_page(); p.insert_text((50,100),f'Page {i} body')
    doc.set_toc([[1,'Second',2]])
    assert main._paragraphs_from_pdf(doc.tobytes())['chapters'][0]['startParagraphIndex']==1

def test_pdf_drops_running_margins_and_rejoins_page_continuation():
    with fitz.open() as doc:
        first = doc.new_page()
        first.insert_text((60, 40), "Running book title")
        first.insert_text((60, 720), "The sentence continues")
        first.insert_text((60, 810), "1")
        second = doc.new_page()
        second.insert_text((60, 40), "Running book title")
        second.insert_text((60, 125), "on the following page.")
        second.insert_text((60, 810), "2")
        book = main._paragraphs_from_pdf(doc.tobytes())
    assert [p['text'] for p in book['paragraphs']] == ["The sentence continues on the following page."]

def test_epub_spine_order_and_paragraphs(tmp_path):
    b=epub.EpubBook(); b.set_identifier('test'); b.set_title('Title'); b.set_language('en')
    a=epub.EpubHtml(title='A',file_name='a.xhtml'); a.content='<h1>First</h1><p>One two.</p><p>Three.</p>'
    z=epub.EpubHtml(title='Z',file_name='z.xhtml'); z.content='<h1>Second</h1><p>Four five.</p>'
    b.add_item(z); b.add_item(a); b.spine=[a,z]; b.add_item(epub.EpubNcx()); b.add_item(epub.EpubNav())
    path=tmp_path/'book.epub'; epub.write_epub(str(path),b)
    book=main._paragraphs_from_epub(path.read_bytes())
    assert book['metadata']['title']=='Title'
    assert [p['text'] for p in book['paragraphs']]==['First','One two.','Three.','Second','Four five.']
    assert book['chapters'][1]['startParagraphIndex']==3

@pytest.mark.parametrize('value',['../escape','a/b','','x'*129])
def test_invalid_identifiers(value):
    with pytest.raises(main.HTTPException): main.identifier(value)

def test_milestones():
    book={'paragraphs':[{'index':i,'wordCount':10} for i in range(8)],'chapters':[{'startParagraphIndex':0},{'startParagraphIndex':3},{'startParagraphIndex':6}]}
    assert main.milestone_end(book,0,'words',21)==2
    assert main.milestone_end(book,0,'chapters',1)==2
    assert main.milestone_end(book,3,'chapters',1)==5
    assert main.milestone_end(book,0,'custom',99)==7

def test_review_window_and_deadline():
    now=datetime.now(timezone.utc); deadline=now+timedelta(hours=1)
    assert main.commentary_eligible({'status':'PENDING_REVIEW','createdAt':now},deadline,now)
    assert not main.commentary_eligible({'status':'PENDING_REVIEW','createdAt':now-timedelta(hours=25)},deadline,now)
    assert not main.commentary_eligible({'status':'REVISION_REQUESTED','createdAt':now},deadline,now)
    assert not main.commentary_eligible({'status':'APPROVED','createdAt':now+timedelta(hours=2)},deadline,now)

def test_nonmember_cannot_download(monkeypatch):
    main.app.dependency_overrides[main.identity]=lambda:{'uid':'outsider'}
    def deny(*args): raise main.HTTPException(403,'Group membership required')
    monkeypatch.setattr(main,'member',deny)
    try: assert client.get('/groups/g/books/b').status_code==403
    finally: main.app.dependency_overrides.clear()
