(async function(){
 let previous=null;try{previous=localStorage.getItem('reading_series');}catch(e){}
 const history=()=>{try{browser.runtime.sendNativeMessage('hako',{history:localStorage.getItem('reading_series')||'[]'}).catch(()=>{});}catch(e){}};
 addEventListener('pagehide',history,{once:true});
 addEventListener('DOMContentLoaded',()=>setTimeout(history,1000),{once:true});
 let command;
 try{command=await browser.runtime.sendNativeMessage('hako',{hello:true});}catch(e){return;}
 if(!command||!command.script)return; // Interactive login has no extraction delegate.
 const send=value=>browser.runtime.sendNativeMessage('hako',value).catch(()=>{});
 const restore=()=>{try{let a=JSON.parse(localStorage.getItem('reading_series')||'[]');if(a.length&&new URL(a[0].chapter_url,location.origin).pathname===location.pathname){if(previous===null)localStorage.removeItem('reading_series');else localStorage.setItem('reading_series',previous);}}catch(e){}};
 let tries=0;
 function read(){
  if(document.readyState==='loading'){setTimeout(read,500);return;}
  if(document.querySelector('#challenge-running, #challenge-form')||/just a moment|performing security verification/i.test(document.title)){
   send({error:'Cần xác minh HAKO. Mở Đăng nhập; tải tự động đã dừng.'});return;
  }
  let value={};try{value=JSON.parse(eval(command.script));}catch(e){}
  if(value.error){send(value);return;}
  if(value.html){if(location.pathname.includes('/c'))restore();send(value);return;}
  if(++tries>=25){send({error:'Trang không có nội dung hợp lệ; không lưu chương rỗng.'});return;}
  setTimeout(read,1000);
 }
 read();
})();
