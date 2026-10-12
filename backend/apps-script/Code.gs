/* Personal report relay. Secrets and destination live in Script Properties, never APK/source. */
function setupReporting() {
  var properties = PropertiesService.getScriptProperties();
  if (!properties.getProperty('REPORT_TOKEN')) properties.setProperty('REPORT_TOKEN', Utilities.getUuid() + Utilities.getUuid());
  console.log('Configure REPORT_TO, then copy REPORT_TOKEN from Script Properties.');
}
function reply_(value) { return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON); }
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
    MailApp.sendEmail({to:destination,subject:'Hako Pocket report ' + id,
      body:'Report ID: ' + id + '\nApp: ' + String(report.version || '').slice(0,64) + '\nDevice: ' + String(report.model || '').slice(0,80) + '\nSee attached JSON for timings and counts.',
      attachments:[Utilities.newBlob(attachment,'application/json','Hako-report-' + id + '.json')]});
    ledger.push(id); if (ledger.length > 128) ledger = ledger.slice(-128);
    properties.setProperties({REPORT_SENT:JSON.stringify(ledger),REPORT_DAY:today,REPORT_COUNT:String(count+1)});
    return reply_({ok:true,reportId:id});
  } catch(error) { return reply_({ok:false,retryable:true,reportId:id,error:'service_error'}); }
  finally { if(held) lock.releaseLock(); }
}
