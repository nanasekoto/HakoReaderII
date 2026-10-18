const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/java/vn/nanase/hako/RenderedPage.java','utf8');
const line=source.split('\n').find(x=>x.includes('var id="+JSONObject.quote(action.substring(5))'));
assert(line,'Actual read action must be found');
const tail=line.split('JSONObject.quote(action.substring(5))+')[1].trim();
const script='(function(){var id="999010"'+JSON.parse(tail.slice(0,-1));
function fixture({ready='complete',login=false,table=true,book=true,next=false,button=false,unread=1,goneAfterClick=false}={}){
 let clicks=0;const window={},location={href:'https://docln.sbs/ke-sach',origin:'https://docln.sbs'};
 const mark={getAttribute:k=>k==='data-series'?'999010':String(unread),parentElement:{querySelector:()=>goneAfterClick&&clicks>0?null:mark},click:()=>clicks++};
 const document={readyState:ready,querySelector:q=>q==='input[type=password]'?(login?{}:null):q==='table'?(table?{}:null):null,
 querySelectorAll:q=>q==='.mark-read'?(button?[mark]:[]):q==='table a[href]'?(book?[{href:'https://docln.sbs/truyen/999010-fixture'}]:[]):q==='a[href]'?(next?[{href:'https://docln.sbs/ke-sach?page=2'}]:[]):[]};
 const run=()=>JSON.parse(vm.runInNewContext(script,{document,window,location,URL}));return {run,location,clicks:()=>clicks};
}
let f=fixture();assert.equal(f.run().html,'NO_READ_BUTTON');assert.equal(f.clicks(),0);
assert.equal(fixture({login:true}).run().error,'Hãy đăng nhập HAKO trước.');
assert(fixture({table:false}).run().error);assert.deepEqual(fixture({ready:'loading'}).run(),{});
f=fixture({book:false,next:true});assert.deepEqual(f.run(),{});assert(f.location.href.includes('page=2'));
assert(fixture({book:false}).run().error);
f=fixture({button:true});assert.deepEqual(f.run(),{});f.run();assert.equal(f.clicks(),1);
assert.equal(fixture({button:true,unread:0}).run().html,'OK');
f=fixture({button:true,goneAfterClick:true});f.run();assert.equal(f.run().html,'OK_BUTTON_GONE');assert.equal(f.clicks(),1);
console.log('Actual read-button script: missing button, login, loading, pagination and single-click checks passed');
