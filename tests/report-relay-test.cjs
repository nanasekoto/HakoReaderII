const fs=require('fs'),vm=require('vm'),assert=require('assert');
const values={REPORT_TOKEN:'private-fixture-token',REPORT_TO:'owner@example.test'};
let sent=[],locked=false,quota=50;
const context={console,JSON,Date,PropertiesService:{getScriptProperties:()=>({getProperty:k=>values[k],setProperty:(k,v)=>values[k]=v,setProperties:o=>Object.assign(values,o)})},
 LockService:{getScriptLock:()=>({tryLock:()=>!locked,releaseLock:()=>{}})},
 Utilities:{getUuid:()=> '00000000-0000-4000-8000-000000000001',formatDate:()=> '2026-10-12',newBlob:(data,type,name)=>({data,type,name})},
 ContentService:{MimeType:{JSON:'json'},createTextOutput:text=>({text,setMimeType(){return this;}})},
 MailApp:{getRemainingDailyQuota:()=>quota,sendEmail:mail=>sent.push(mail)}};
vm.createContext(context);vm.runInContext(fs.readFileSync('backend/apps-script/Code.gs','utf8'),context);
function post(input){return JSON.parse(context.doPost({postData:{contents:JSON.stringify(input)}}).text);}
const report={schema:1,reportId:'00000000-0000-4000-8000-000000000002',version:'0.7.23',model:'fixture',to:'attacker@example.test'};
assert.equal(post({token:'wrong',report}).ok,false);assert.equal(sent.length,0);
assert.equal(post({report}).error,'unauthorized');assert.equal(sent.length,0);
assert.equal(post({token:values.REPORT_TOKEN,report}).ok,true);assert.equal(sent.length,1);assert.equal(sent[0].to,values.REPORT_TO);
assert.equal(sent[0].attachments.length,1);assert.equal(JSON.parse(sent[0].attachments[0].data).reportId,report.reportId);
assert(sent[0].htmlBody.includes('Truy vấn SQLite'));assert(sent[0].body.includes('Report ID:'));
const malicious=context.summary_({...report,model:'<img src=x onerror=alert(1)>',issues:['<script>bad</script>'],timings:{parse:{count:2,totalMs:100,maxMs:70}},workflow:{pages:3}});
assert(!malicious.html.includes('<img src=x'));assert(!malicious.html.includes('<script>'));assert(malicious.html.includes('&lt;script&gt;'));assert(malicious.html.includes('Trang kệ đã quét'));assert(malicious.html.includes('0.10'));
assert.equal(post({token:values.REPORT_TOKEN,report}).duplicate,true);assert.equal(sent.length,1);
assert.equal(post({token:values.REPORT_TOKEN,report:{...report,schema:9}}).ok,false);
assert.equal(post({token:values.REPORT_TOKEN,report:{...report,reportId:'invalid'}}).ok,false);
locked=true;assert.equal(post({token:values.REPORT_TOKEN,report:{...report,reportId:'00000000-0000-4000-8000-000000000003'}}).error,'busy');locked=false;
values.REPORT_COUNT='12';assert.equal(post({token:values.REPORT_TOKEN,report:{...report,reportId:'00000000-0000-4000-8000-000000000004'}}).error,'daily_limit');
values.REPORT_COUNT='1';quota=0;assert.equal(post({token:values.REPORT_TOKEN,report:{...report,reportId:'00000000-0000-4000-8000-000000000004'}}).error,'daily_limit');quota=50;
delete values.REPORT_TO;assert.equal(post({token:values.REPORT_TOKEN,report}).error,'configure_destination');values.REPORT_TO='owner@example.test';
assert.equal(context.doPost({postData:{contents:'bad-json'}}).text.includes('service_error'),true);
console.log('Report relay authentication, recipient, attachment, deduplication, limits and recovery tests passed');

