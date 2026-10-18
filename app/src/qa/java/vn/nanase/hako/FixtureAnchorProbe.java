package vn.nanase.hako;

import android.content.*;
import org.json.*;
import java.util.*;

/** An incoming position precedes the downloaded file, just as on a second device. */
public final class FixtureAnchorProbe {
    public static final String BOOK="truyen-999991",CHAPTER=BOOK+"-1";
    static String lastBook="";
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static int prepare(Context c)throws Exception{
        FixtureLibrary.requireLab(c);Store store=Store.get(c);lastBook=c.getSharedPreferences("settings",0).getString("lastBook","");
        Store.delete(store.dir(CHAPTER));String url="https://docln.sbs/truyen/999991-anchor";
        JSONObject book=new JSONObject().put("id",BOOK).put("title","[MẪU] Neo trước khi có file").put("url",url);
        JSONArray chapters=new JSONArray().put(new JSONObject().put("id",CHAPTER).put("title","Chương thử neo").put("url",url+"/c1-lab").put("kind","text"));
        JSONObject loc=new JSONObject().put("paragraph",3).put("text_paragraph",1).put("fraction",.999).put("quote","");
        JSONObject data=new JSONObject().put("book",book).put("chapters",chapters).put("read",new JSONArray())
            .put("position",new JSONObject().put("legacy_chapter",CHAPTER).put("chapter",BOOK+"-c1").put("portable",loc).put("revision",1).put("device","fixture-other-device"));
        ServerBridge.apply(c,data,true);
        require(!store.html(CHAPTER).exists(),"Incoming metadata must not manufacture a local chapter file");
        require(ServerBridge.prefs(c).contains("resume_locator:"+BOOK),"Cold position retains its logical locator");
        JSONObject stale=new JSONObject(data.toString());stale.getJSONObject("position").put("revision",0).put("portable",new JSONObject().put("paragraph",0).put("text_paragraph",0).put("fraction",0));
        ServerBridge.apply(c,stale,true);require(store.book(BOOK).pos==3,"A stale remote response cannot replace the newer incoming checkpoint");
        String active=ServerBridge.activeBook;try{ServerBridge.activeBook=BOOK;stale.getJSONObject("position").put("revision",2);ServerBridge.apply(c,stale,true);require(store.book(BOOK).pos==3,"Even a fresh remote position cannot move an active reader");}finally{ServerBridge.activeBook=active;}
        StringBuilder text=new StringBuilder();for(int i=0;i<100;i++)text.append("Đoạn dài cần tiếp tục giữa hai thiết bị. Cùng một nội dung nhưng kích thước màn hình và số trang khác nhau. ");
        String image="<p><img src=\"https://offline.hako.invalid/"+CHAPTER+"/image-aaaaaaaaaaaaaaaaaaaaaaaa.bin\"></p>";
        String html=(DeviceProfile.images(c)?image:"")+"<p>Đoạn mở đầu trước vị trí cần đọc tiếp, có dấu tiếng Việt.</p>"+(DeviceProfile.images(c)?image:"")+"<p>"+text+"Hết.</p><p>Đoạn cuối.</p>";
        if(DeviceProfile.images(c))FixtureLibrary.image(new java.io.File(store.dir(CHAPTER),"image-aaaaaaaaaaaaaaaaaaaaaaaa.bin"),40,60,android.graphics.Color.BLUE);
        Store.write(store.html(CHAPTER),html);store.state(CHAPTER,store.readable(CHAPTER),"");
        require(store.readable(CHAPTER),"Simulated delivered chapter passes the actual cache validator");
        return DeviceProfile.images(c)?3:1;
    }
    public static void verify(Context c,int expected)throws Exception{
        require(!ServerBridge.prefs(c).contains("resume_locator:"+BOOK),"Opening resolves the deferred locator after content exists");
        require(Store.get(c).book(BOOK).pos==expected,"Image and text-only layouts resume in the same logical text paragraph");
    }
    public static void clear(Context c)throws Exception{
        FixtureLibrary.requireLab(c);Store store=Store.get(c);Store.delete(store.dir(CHAPTER));
        store.getWritableDatabase().execSQL("DELETE FROM read_chapters WHERE id=?",new Object[]{CHAPTER});
        store.getWritableDatabase().execSQL("DELETE FROM chapters WHERE book=?",new Object[]{BOOK});
        store.getWritableDatabase().execSQL("DELETE FROM books WHERE id=?",new Object[]{BOOK});
        SharedPreferences p=ServerBridge.prefs(c);SharedPreferences.Editor editor=p.edit();for(String key:p.getAll().keySet())if(key.contains(BOOK))editor.remove(key);editor.commit();
        c.getSharedPreferences("settings",0).edit().putString("lastBook",lastBook).remove("remote_metadata_only_"+BOOK).commit();store.resetDerived();LibraryState.refresh(c);
    }
}
