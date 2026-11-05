
const {before,after,test}=require('node:test');
const fs=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,updateDoc,serverTimestamp}=require('firebase/firestore');
const {ref,getBytes,uploadBytes}=require('firebase/storage');
let env;
before(async()=>{
 env=await initializeTestEnvironment({
  projectId:'demo-coread',
  firestore:{rules:fs.readFileSync('firebase/firestore.rules','utf8')},
  storage:{rules:fs.readFileSync('firebase/storage.rules','utf8')}
 });
 await env.withSecurityRulesDisabled(async ctx=>{
  const db=ctx.firestore();
  await setDoc(doc(db,'groups/g'),{memberIds:['alice','bob'],invitedEmails:['invited@example.com'],paragraphCount:10,endParagraphIndex:4});
  await setDoc(doc(db,'groups/g/members/alice'),{verifiedThrough:1,email:'alice@example.com'});
  await setDoc(doc(db,'groups/g/members/bob'),{verifiedThrough:4,email:'bob@example.com'});
  await setDoc(doc(db,'groups/g/commentaries/1_bob'),{uid:'bob',text:'Spoiler',dayNumber:1,endParagraphIndex:4});
  await uploadBytes(ref(ctx.storage(),'groups/g/books/book.json'),new Uint8Array([1,2,3]));
 });
});
 
after(async()=>{if(env) await env.cleanup()});
test('book history is readable only by circle members',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'groups/g/readingHistory/old'),{title:'A past book',status:'stopped',verifiedThroughByUid:{alice:66}}));
 await assertSucceeds(getDoc(doc(ctx('alice').firestore(),'groups/g/readingHistory/old')));
 await assertFails(getDoc(doc(ctx('charlie').firestore(),'groups/g/readingHistory/old')));
});
test('readers cannot forge history or earned streak badges',async()=>{
 await assertFails(setDoc(doc(ctx('alice').firestore(),'groups/g/readingHistory/fake'),{status:'completed'}));
 await assertFails(updateDoc(doc(ctx('alice').firestore(),'groups/g'),{bestStreak:30}));
});
const ctx=(uid,verified=true)=>env.authenticatedContext(uid,{email:uid+'@example.com',email_verified:verified});
test('member can read group',async()=>assertSucceeds(getDoc(doc(ctx('alice').firestore(),'groups/g'))));
test('anonymous cannot read group',async()=>assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'groups/g'))));
test('outsider cannot read group',async()=>assertFails(getDoc(doc(ctx('charlie').firestore(),'groups/g'))));
test('unverified email cannot read',async()=>assertFails(getDoc(doc(ctx('alice',false).firestore(),'groups/g'))));
test('member cannot rewrite streak or membership',async()=>assertFails(updateDoc(doc(ctx('alice').firestore(),'groups/g'),{streakCount:999,memberIds:['alice']})));
test('invitation requires authenticated server acceptance',async()=>assertFails(updateDoc(doc(ctx('invited').firestore(),'groups/g'),{memberIds:['alice','bob','invited']})));
test('own monotonic progress allowed',async()=>assertSucceeds(updateDoc(doc(ctx('alice').firestore(),'groups/g/members/alice'),{verifiedThrough:2})));
test('progress cannot exceed book',async()=>assertFails(updateDoc(doc(ctx('alice').firestore(),'groups/g/members/alice'),{verifiedThrough:10})));
test('cannot edit partner progress',async()=>assertFails(updateDoc(doc(ctx('alice').firestore(),'groups/g/members/bob'),{verifiedThrough:5})));
test('cannot change own email through member write',async()=>assertFails(updateDoc(doc(ctx('alice').firestore(),'groups/g/members/alice'),{email:'bob@example.com'})));
test('reflection spoiler hidden until reading complete',async()=>assertFails(getDoc(doc(ctx('alice').firestore(),'groups/g/commentaries/1_bob'))));
test('author can read own reflection',async()=>assertSucceeds(getDoc(doc(ctx('bob').firestore(),'groups/g/commentaries/1_bob'))));
test('client cannot approve own reflection',async()=>assertFails(updateDoc(doc(ctx('bob').firestore(),'groups/g/commentaries/1_bob'),{status:'APPROVED'})));
test('member can send own timestamped message',async()=>assertSucceeds(setDoc(doc(ctx('alice').firestore(),'groups/g/messages/m'),{uid:'alice',text:'Hello',paragraphIndex:-1,createdAt:serverTimestamp()})));
test('cannot impersonate chat partner',async()=>assertFails(setDoc(doc(ctx('alice').firestore(),'groups/g/messages/spoof'),{uid:'bob',text:'Hello',paragraphIndex:-1,createdAt:serverTimestamp()})));
test('member can read private book',async()=>assertSucceeds(getBytes(ref(ctx('alice').storage(),'groups/g/books/book.json'))));
test('outsider cannot read book',async()=>assertFails(getBytes(ref(ctx('charlie').storage(),'groups/g/books/book.json'))));
test('member cannot replace private book',async()=>assertFails(uploadBytes(ref(ctx('alice').storage(),'groups/g/books/book.json'),new Uint8Array([4]))));

test('previous-day reflection stays readable after daily range advances',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),'groups/g'),{endParagraphIndex:9});
  await updateDoc(doc(c.firestore(),'groups/g/members/alice'),{verifiedThrough:4});
 });
 await assertSucceeds(getDoc(doc(ctx('alice').firestore(),'groups/g/commentaries/1_bob')));
});
test('unlocked reflection query must include safe range bound',async()=>{
 const {query,collection,where,getDocs}=require('firebase/firestore');
 await assertSucceeds(getDocs(query(collection(ctx('alice').firestore(),'groups/g/commentaries'),where('dayNumber','==',1),where('endParagraphIndex','<=',4))));
 await assertFails(getDocs(query(collection(ctx('alice').firestore(),'groups/g/commentaries'),where('dayNumber','==',1))));
});

test('progress for a removed book cannot be applied to its replacement',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),'groups/g'),{activeBookId:'replacement',managementOperation:null});
  await updateDoc(doc(c.firestore(),'groups/g/members/alice'),{bookId:'replacement',verifiedThrough:-1});
 });
 const member=doc(ctx('alice').firestore(),'groups/g/members/alice');
 await assertFails(updateDoc(member,{bookId:'old-book',verifiedThrough:5}));
 await assertSucceeds(updateDoc(member,{bookId:'replacement',verifiedThrough:1}));
});
test('circle maintenance blocks chat and progress while owner can still see group',async()=>{
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'groups/g'),{managementOperation:'remove_book'}));
 const db=ctx('alice').firestore();
 await assertSucceeds(getDoc(doc(db,'groups/g')));
 await assertFails(updateDoc(doc(db,'groups/g/members/alice'),{verifiedThrough:2,bookId:'replacement'}));
 await assertFails(setDoc(doc(db,'groups/g/messages/during-delete'),{uid:'alice',text:'Hello',paragraphIndex:-1,createdAt:serverTimestamp()}));
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'groups/g'),{managementOperation:null}));
});

test('sync transaction preserves newer progress instead of retrying a rejected rollback',async()=>{
 const {runTransaction}=require('firebase/firestore');
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),'groups/g'),{activeBookId:'current',paragraphCount:2954});
  await updateDoc(doc(c.firestore(),'groups/g/members/alice'),{bookId:'current',verifiedThrough:66});
 });
 const db=ctx('alice').firestore();const circle=doc(db,'groups/g');const member=doc(db,'groups/g/members/alice');
 await assertFails(updateDoc(member,{bookId:'current',verifiedThrough:-1,lastActive:serverTimestamp()}));
 const merged=await assertSucceeds(runTransaction(db,async tx=>{
  const group=await tx.get(circle);const saved=await tx.get(member);
  if(group.data().activeBookId!=='current') throw Error('Book changed');
  const through=Math.max(-1,saved.data().verifiedThrough);
  tx.update(member,{bookId:'current',verifiedThrough:through,lastActive:serverTimestamp()});
  return through;
 }));
 if(merged!==66) throw Error('Saved progress was lost');
 await assertSucceeds(runTransaction(db,async tx=>{
  const saved=await tx.get(member);
  tx.update(member,{bookId:'current',verifiedThrough:Math.max(70,saved.data().verifiedThrough),lastActive:serverTimestamp()});
 }));
 if((await getDoc(member)).data().verifiedThrough!==70) throw Error('New reading was not synchronized');
});

