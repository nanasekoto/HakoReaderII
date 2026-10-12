/* Personal report relay. Secrets and destination live in Script Properties, never APK/source. */
function setupReporting() {
  var properties = PropertiesService.getScriptProperties();
  if (!properties.getProperty('REPORT_TOKEN')) properties.setProperty('REPORT_TOKEN', Utilities.getUuid() + Utilities.getUuid());
  console.log('Configure REPORT_TO, then copy REPORT_TOKEN from Script Properties.');
}
function reply_(value) { return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON); }
function escape_(value) { return String(value == null ? '' : value).slice(0,2000).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
function summary_(report) {
  function number(value) { return typeof value === 'number' && isFinite(value) ? Math.max(0,value) : 0; }
  var rows = [['Phiên bản',report.version],['Thiết bị',report.model],['Android',report.android],['Thời gian phiên (giây)',Math.round(number(report.uptimeMs)/1000)],['CPU tiến trình (giây)',(number(report.mainProcessCpuMs)/1000).toFixed(2)],['RAM PSS (MB)',(number(report.mainPssKb)/1024).toFixed(1)],['Truy vấn SQLite',number((report.counts||{})['db.query'])]], workflow=report.workflow||{};
  [['pages','Trang kệ đã quét'],['catalogsChecked','Mục lục đã kiểm tra'],['catalogsSkipped','Mục lục dùng lại'],['booksSelected','Truyện chọn tải'],['chaptersDone','Chương hoàn tất'],['chaptersQueued','Chương trong lượt tải']].forEach(function(pair){if(Object.prototype.hasOwnProperty.call(workflow,pair[0]))rows.push([pair[1],number(workflow[pair[0]])]);});
  var html='<div style="font-family:Arial,sans-serif"><h2>Hako — báo cáo theo yêu cầu</h2><table>',text='Hako — báo cáo theo yêu cầu\n';
  rows.forEach(function(row){html+='<tr><td style="padding:5px">'+escape_(row[0])+'</td><td style="padding:5px">'+escape_(row[1])+'</td></tr>';text+=row[0]+': '+String(row[1] == null ? '' : row[1]).slice(0,120)+'\n';});html+='</table>';
  var timings=report.timings||{},names=Object.keys(timings).slice(0,32).sort(function(a,b){return number((timings[b]||{}).totalMs)-number((timings[a]||{}).totalMs);}).slice(0,16);
  html+='<h3>Thời gian từng đoạn</h3><table><tr><th>Tác vụ</th><th>Lượt</th><th>Tổng giây</th><th>Chậm nhất ms</th></tr>';
  names.forEach(function(name){var timing=timings[name]||{},total=(number(timing.totalMs)/1000).toFixed(2);html+='<tr><td>'+escape_(name)+'</td><td>'+number(timing.count)+'</td><td>'+total+'</td><td>'+number(timing.maxMs)+'</td></tr>';text+=name+': '+total+' giây tổng, '+number(timing.maxMs)+' ms chậm nhất\n';});html+='</table><p>Các đoạn có thể lồng nhau hoặc chạy đồng thời; tổng thời gian không phải phần trăm CPU.</p>';
  var issues=Array.isArray(report.issues)?report.issues.slice(0,16):[],previous=report.previousCrash||{};if(Array.isArray(previous.issues))issues=issues.concat(previous.issues.slice(0,16));if(report.uiStall){issues.push('Giao diện chậm trả lời: '+number(report.uiStall.delayMs)+' ms');if(Array.isArray(report.uiStall.mainThread))issues=issues.concat(report.uiStall.mainThread.slice(0,24));}
  if(issues.length){html+='<h3>Dấu vết lỗi / giao diện đứng</h3><pre>'+issues.map(escape_).join('\n')+'</pre>';text+='\nDấu vết lỗi:\n'+issues.map(function(x){return String(x).slice(0,2000);}).join('\n');}
  html+='<p>Mã báo cáo: '+escape_(report.reportId)+'. JSON chi tiết đính kèm.</p></div>';return {html:html,text:text+'\nReport ID: '+report.reportId};
}
function doPost(e) {
  var lock = LockService.getScriptLock(), held = false, id = '';
  try {
    if (!e || !e.postData || e.postData.contents.length > 100000) return reply_({ok:false,retryable:false,error:'invalid_size'});
    var input = JSON.parse(e.postData.contents), properties = PropertiesService.getScriptProperties();
    var expected = properties.getProperty('REPORT_TOKEN'), destination = properties.getProperty('REPORT_TO');
    if (!expected || input.token !== expected) return reply_({ok:false,retryable:false,error:'unauthorized'});
    if (!destination || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(destination)) return reply_({ok:false,retryable:false,error:'configure_destination'});
    var report = input.report;
    if (!report || report.schema !== 1 || typeof report.reportId !== 'string' || !/^[a-f0-9-]{36}$/.test(report.reportId)) return reply_({ok:false,retryable:false,error:'invalid_report'});
    id = report.reportId;
    if (!lock.tryLock(1000)) return reply_({ok:false,retryable:true,reportId:id,error:'busy'}); held = true;
    var ledger = JSON.parse(properties.getProperty('REPORT_SENT') || '[]');
    if (ledger.indexOf(id) !== -1) return reply_({ok:true,reportId:id,duplicate:true});
    var today = Utilities.formatDate(new Date(), 'Asia/Ho_Chi_Minh', 'yyyy-MM-dd');
    var day = properties.getProperty('REPORT_DAY'), count = day === today ? Number(properties.getProperty('REPORT_COUNT') || '0') : 0;
    if (count >= 12 || MailApp.getRemainingDailyQuota() < 1) return reply_({ok:false,retryable:true,reportId:id,error:'daily_limit'});
    var attachment = JSON.stringify(report, null, 2);
    if (attachment.length > 98304) return reply_({ok:false,retryable:false,reportId:id,error:'invalid_size'});
    // Destination is never taken from the incoming report; this endpoint is not an open mail relay.
    var summary=summary_(report);
    MailApp.sendEmail({to:destination,subject:'Hako Pocket report ' + id,
      body:summary.text,htmlBody:summary.html,
      attachments:[Utilities.newBlob(attachment,'application/json','Hako-report-' + id + '.json')]});
    ledger.push(id); if (ledger.length > 128) ledger = ledger.slice(-128);
    properties.setProperties({REPORT_SENT:JSON.stringify(ledger),REPORT_DAY:today,REPORT_COUNT:String(count+1)});
    return reply_({ok:true,reportId:id});
  } catch(error) { return reply_({ok:false,retryable:true,reportId:id,error:'service_error'}); }
  finally { if(held) lock.releaseLock(); }
}

