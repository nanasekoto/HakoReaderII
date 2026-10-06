(function(){
 if(document.readyState==='loading'||!document.body)return JSON.stringify({});
 var title=document.title||'',text=(document.body.innerText||'').slice(0,2500);
 if(document.querySelector('#challenge-running,#challenge-form')||/just a moment|performing security verification/i.test(title)||/verify you are human|checking your browser|access denied|too many requests/i.test(text))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc giới hạn truy cập. Mở Tài khoản để kiểm tra; không tự tải lại.'});
 return JSON.stringify({html:document.documentElement.outerHTML});
})()
