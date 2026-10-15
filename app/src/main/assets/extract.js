(function(){
 var e=document.getElementById('chapter-content');
 if(!e){var t=(document.body?document.body.innerText:'').slice(0,3000);if(/verify you are human|checking your browser|just a moment|too many requests|access denied/i.test(t))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc đang giới hạn truy cập. Mở HAKO để kiểm tra.'});return JSON.stringify({});}
 var protectedNode=e.querySelector('#chapter-c-protected');
 if(protectedNode&&!protectedNode.innerText.trim()&&!protectedNode.querySelector('img[src],img[data-src]'))return JSON.stringify({});
 var copy=e.cloneNode(true);
 copy.querySelectorAll('[data-tooltip-content]').forEach(function(n){var sel=n.getAttribute('data-tooltip-content');if(!/^#note[0-9]+ \.note-content$/.test(sel))return;var source=document.querySelector(sel);if(!source)return;var d=document.createElement('details'),s=document.createElement('summary'),v=document.createElement('div');s.textContent='✎';v.innerHTML=source.innerHTML;d.appendChild(s);d.appendChild(v);n.replaceWith(d);});
 copy.querySelectorAll('script,style,iframe,form,video,audio,object,embed,[hidden]').forEach(function(n){n.remove();});
 copy.querySelectorAll('[style]').forEach(function(n){if(/display\s*:\s*none|visibility\s*:\s*hidden/i.test(n.getAttribute('style')))n.remove();});
 copy.querySelectorAll('img').forEach(function(n){var src=n.getAttribute('data-src')||n.getAttribute('src')||'';if(src.indexOf('/chapter-banners/')>=0||src.indexOf('/series/covers/')>=0)n.remove();});
 if(!copy.textContent.trim()&&!copy.querySelector('img[src],img[data-src]'))return JSON.stringify({});
 return JSON.stringify({html:copy.outerHTML});
})();
