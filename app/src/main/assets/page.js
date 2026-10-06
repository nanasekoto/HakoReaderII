(function(){
 if(document.readyState==='loading'||!document.body)return JSON.stringify({});
 var nav=performance.getEntriesByType('navigation')[0];
 if(nav&&typeof nav.responseStatus==='number'&&nav.responseStatus>=400)return JSON.stringify({error:'Trang trả HTTP '+nav.responseStatus+'. Không nhập dữ liệu lỗi; mở Tài khoản để kiểm tra.'});
 if(/^(?:error[ :]+)?(?:403|500|502|503|504)\b|^(?:service unavailable|bad gateway|gateway timeout|internal server error|forbidden)$/i.test(document.title||''))return JSON.stringify({error:'Máy chủ trả trang lỗi. Không nhập tủ sách từ trang này.'});
 var title=document.title||'',text=(document.body.innerText||'').slice(0,2500);
 if(document.querySelector('#challenge-running,#challenge-form')||/just a moment|performing security verification/i.test(title)||/verify you are human|checking your browser|access denied|too many requests/i.test(text))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc giới hạn truy cập. Mở Tài khoản để kiểm tra; không tự tải lại.'});
 return JSON.stringify({html:document.documentElement.outerHTML});
})()
