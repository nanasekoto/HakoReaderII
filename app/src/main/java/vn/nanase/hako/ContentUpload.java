package vn.nanase.hako;

import android.app.job.*;
import android.content.*;
import android.os.SystemClock;
import android.util.Base64;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable file outbox. Never owns the position lane or initializes a browser. */
public final class ContentUpload extends JobService {
    static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static final AtomicBoolean running=new AtomicBoolean();
    static volatile boolean stopped;
    static final int JOB=60, CHUNK=256*1024;
    interface Transport {JSONObject post(Context context,String path,JSONObject value)throws Exception;default boolean connected(Context context){return ReadSync.connected(context);}}
    static Transport transport=(c,path,value)->ServerSource.request(c,"POST",path,value);
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("content_upload",0);}
    static String owner(Context c)throws Exception{
        SharedPreferences p=ServerBridge.prefs(c);
        return hash((p.getString("server","https://nanains.duckdns.org/hako")+"\n"+p.getString("token","")).getBytes("UTF-8"));
    }
    static String hash(byte[] raw)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(raw));}
    static String hex(byte[] raw){StringBuilder s=new StringBuilder();for(byte b:raw)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}
    static void enqueue(Context c,String book,String chapter){
        if(!ServerBridge.enabled(c)||!chapter.matches("(?:truyen|sang-tac|ai-dich)-[0-9]{1,20}-[0-9]{1,20}"))return;
        if(book.equals(prefs(c).getString("pending:"+chapter,"")))return;
        prefs(c).edit().putString("pending:"+chapter,book).commit();
        retry(c);
    }
    static boolean pending(Context c){for(String key:prefs(c).getAll().keySet())if(key.startsWith("pending:"))return true;return false;}
    static void retry(Context c){
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(jobs==null||jobs.getPendingJob(JOB)!=null||!ServerBridge.enabled(c))return;
        JobInfo.Builder job=new JobInfo.Builder(JOB,new ComponentName(c,ContentUpload.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(30000)
            .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true);
        if(DeviceProfile.eink(c))job.setRequiresCharging(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        jobs.schedule(job.build());
    }
    static void kick(Context context){Context c=context.getApplicationContext();if(!ServerBridge.enabled(c)||!running.compareAndSet(false,true))return;
        stopped=false;worker.execute(()->{try{drain(c);}catch(Exception e){prefs(c).edit().putString("status","Chờ gửi nội dung · File vẫn giữ trên máy").apply();}
            finally{running.set(false);if(pending(c))retry(c);}});
    }
    static boolean priorityPending(Context c){for(String key:ServerBridge.prefs(c).getAll().keySet())if(key.startsWith("pending:"))return true;return false;}
    static void seedExisting(Context c,String captured){
        // One bounded metadata slice per idle/charging run, never a UI/file scan.
        if(!ReadSync.wifi(c)||!ChargeJob.isCharging(c))return;
        Store store=Store.get(c);SharedPreferences p=prefs(c);String key="inventory:"+captured;
        String[] cursor=p.getString(key,"0:0").split(":");
        int bookIndex=Integer.parseInt(cursor[0]),chapterIndex=Integer.parseInt(cursor[1]);
        List<Store.Book> books=store.books();books.sort(Comparator.comparing(b->b.id));if(bookIndex>=books.size())return;
        int queued=0;for(String pending:p.getAll().keySet())if(pending.startsWith("pending:"))queued++;
        if(queued>=8)return;
        Store.Book book=books.get(bookIndex);List<Store.Chapter> chapters=store.chapters(book.id);
        SharedPreferences.Editor edit=p.edit();int end=Math.min(chapters.size(),chapterIndex+64);
        for(int i=chapterIndex;i<end;i++){Store.Chapter ch=chapters.get(i);if(ch.ready)edit.putString("pending:"+ch.id,book.id);}
        if(end>=chapters.size()){bookIndex++;end=0;}
        edit.putString(key,bookIndex+":"+end).commit();
    }
    static void guard(Context c,String captured)throws Exception{
        if(stopped||!ServerBridge.enabled(c)||!captured.equals(owner(c)))throw new IOException("Tạm ngừng gửi nội dung");
        if(!ServerBridge.activeBook.isEmpty()||priorityPending(c))throw new IOException("Ưu tiên đọc và gửi vị trí");
    }
    static JSONObject request(Context c,String captured,String path,JSONObject value)throws Exception{
        guard(c,captured);
        return transport.post(c,"/v3/pocket/upload/"+path,value);
    }
    static void drain(Context c)throws Exception{
        if(!transport.connected(c)||!ServerBridge.enabled(c))return;
        String captured=owner(c);guard(c,captured);seedExisting(c,captured);Store store=Store.get(c);SharedPreferences p=prefs(c);
        long until=SystemClock.elapsedRealtime()+20000;int count=0;
        ArrayList<Map.Entry<String,?>> entries=new ArrayList<>(p.getAll().entrySet());
        String latest=c.getSharedPreferences("settings",0).getString("lastBook","");
        Store.Book last=store.book(latest);String current=last==null?"":last.current;
        entries.sort(Comparator.comparingInt(e->e.getKey().equals("pending:"+current)?0:latest.equals(e.getValue())?1:2));
        for(Map.Entry<String,?> entry:entries){
            if(!entry.getKey().startsWith("pending:"))continue;
            if(count++>=4||SystemClock.elapsedRealtime()>until)break;
            guard(c,captured);String cid=entry.getKey().substring(8),bid=String.valueOf(entry.getValue());
            Store.Chapter chapter=store.chapter(bid,cid);File html=store.html(cid);
            if(chapter==null||!html.isFile()){p.edit().remove(entry.getKey()).commit();continue;}
            if(html.length()>4*1024*1024||!store.readable(cid)){
                p.edit().remove(entry.getKey()).putString("status","Chương local chưa hợp lệ để gửi · "+chapter.title).commit();continue;
            }
            ArrayList<File> files=new ArrayList<>();ArrayList<String> sources=new ArrayList<>();
            files.add(html);sources.add("");
            if(DeviceProfile.images(c))for(Element img:Jsoup.parseBodyFragment(Store.read(html)).select("img[src]")){
                String name=HakoParser.offlineImageName(cid,img.attr("src"));
                File file=name.isEmpty()?null:new File(store.dir(cid),name);
                String source=img.attr("data-hako-src");
                if(file!=null&&!files.contains(file)&&file.length()<=16*1024*1024&&Store.imageFileReadable(file)&&HakoParser.imageAllowed(source)){
                    files.add(file);sources.add(source);
                }
            }
            if(files.size()>65)throw new IOException("Chương có quá nhiều ảnh để gửi một gói");
            StringBuilder stamp=new StringBuilder(captured);
            for(File f:files)stamp.append('|').append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified());
            String fingerprint=hash(stamp.toString().getBytes("UTF-8"));
            if(fingerprint.equals(p.getString("ack:"+cid,""))){p.edit().remove(entry.getKey()).commit();continue;}
            Store.Book book=store.book(bid);if(book==null)continue;
            // Metadata first, without source requests; do not alter another device's position.
            guard(c,captured);transport.post(c,"/v3/pocket/metadata",ServerBridge.metadata(c,book));
            JSONArray manifest=new JSONArray();
            for(int i=0;i<files.size();i++)manifest.put(new JSONObject().put("name",files.get(i).getName()).put("size",files.get(i).length()).put("sha256",hash(files.get(i))).put("source",sources.get(i)));
            JSONObject response=request(c,captured,"begin",new JSONObject().put("chapter",cid).put("files",manifest));
            String session=response.getString("session");
            if(!response.optBoolean("accepted")){
                JSONArray offsets=response.getJSONArray("offsets");
                for(int i=0;i<files.size();i++)try(RandomAccessFile file=new RandomAccessFile(files.get(i),"r")){
                    long offset=offsets.getLong(i);if(offset<0||offset>file.length())throw new IOException("Offset không hợp lệ");file.seek(offset);
                    byte[] buffer=new byte[CHUNK];int n;
                    while((n=file.read(buffer))!=-1){
                        JSONObject sent=request(c,captured,session+"/chunk",new JSONObject().put("index",i).put("offset",offset).put("data",Base64.encodeToString(buffer,0,n,Base64.NO_WRAP)));
                        long next=sent.getLong("offset");if(next!=offset+n)throw new IOException("Server chưa nhận đúng khối");offset=next;
                        if(SystemClock.elapsedRealtime()>until)throw new IOException("Tiếp tục ở lượt nền sau");
                    }
                }
                response=request(c,captured,session+"/commit",new JSONObject());
            }
            if(!response.optBoolean("accepted")||!cid.equals(response.optString("chapter")))throw new IOException("Server chưa xác nhận nội dung");
            // A changed local file will be queued again; never acknowledge its replacement.
            StringBuilder currentFiles=new StringBuilder(captured);for(File f:files)currentFiles.append('|').append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified());
            if(fingerprint.equals(hash(currentFiles.toString().getBytes("UTF-8"))))p.edit().putString("ack:"+cid,fingerprint).putString("ack_hash:"+cid,manifest.getJSONObject(0).getString("sha256")).remove(entry.getKey()).putString("status",(response.optBoolean("canonical_retained")?"Đã sao lưu · Giữ bản hiện có trên server · ":"Đã gửi nội dung · ")+chapter.title).commit();
        }
    }
    public boolean onStartJob(JobParameters params){
        if(!running.compareAndSet(false,true)){jobFinished(params,true);return false;}
        stopped=false;worker.execute(()->{try{drain(this);}catch(Exception e){prefs(this).edit().putString("status","Chờ gửi nội dung · File vẫn giữ trên máy").apply();}
            finally{running.set(false);jobFinished(params,pending(this)&&ServerBridge.enabled(this));}});return true;
    }
    public boolean onStopJob(JobParameters params){stopped=true;return true;}
}
