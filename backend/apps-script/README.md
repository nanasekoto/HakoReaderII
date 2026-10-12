# Activate the personal email receiver once

1. Sign in to https://script.google.com with your own Google account; create a new project.
2. Paste Code.gs from this directory. In Project Settings enable viewing the manifest and paste appsscript.json. This relay requests sending-mail permission, not reading Gmail.
3. Run setupReporting. In Project Settings → Script Properties, add REPORT_TO with your receiving email. REPORT_TOKEN has been generated automatically; copy it privately. Do not publish the token or paste it into GitHub.
4. Deploy → New deployment → Web app. Execute as yourself; access Anyone so Android can POST without a browser login. Authorize sending mail for your script. Copy the production URL ending /exec (not /dev).
5. In Hako's small Báo cáo button → Tùy chọn → Thiết lập gửi email, enter that URL and REPORT_TOKEN. Automatic reports are sent at most once per day when the app is in background with Wi-Fi. Gửi ngay requests an immediate queued report over Wi-Fi.
6. Tap Gửi ngay and check your mailbox/spam folder. The app removes a queued report only after a matching successful response from the service. An accepted send does not prove inbox delivery.

No paid server, domain or Gmail password in APK is required for this personal relay. Apps Script quotas apply. The server fixes the recipient in private properties, limits sending to 12 messages/day, serializes sends and remembers 128 report IDs to avoid ordinary retry duplicates. Email sending and saving the receipt are not a single transaction; a server failure between them can produce a duplicate. This is not a public multi-user reporting platform. A leaked device token can consume the personal endpoint quota; rotate it and update app settings if necessary.

Queued reports are bounded to eight files. Diagnostics have bounded counters/stage aggregates, previous-session metadata and Android process exit reason when available; there is no constant heartbeat/watchdog and no chapter text, account cookie or endpoint token in the report. Automatic reports can be turned off. URL/token live only in app private settings and script properties. Script deployment remains separate from APK build; without it the app can still collect and export reports locally.

Rollback: turn off automatic reports or clear the endpoint; remove the web deployment. This does not affect chapter cache, reading progress or existing downloads.
