package vn.nanase.hako;

import android.content.*;
import java.util.*;

/** Destructive maintenance scenarios are confined to a temporary QA book. */
public final class FixtureCacheProbe {
    static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    public static String run(Context c)throws Exception{
        FixtureLibrary.requireLab(c);Store store=Store.get(c);String bid="truyen-999990";
        SharedPreferences settings=c.getSharedPreferences("settings",0),uploads=ContentUpload.prefs(c);
        int days=settings.getInt("cache_retention_days",0);String active=ServerBridge.activeBook;
        try{
            store.putBook(new HakoParser.Link(bid,"Kho dọn kiểm thử riêng","https://docln.sbs/truyen/999990-cache"));
            ArrayList<HakoParser.Link> chapters=new ArrayList<>();for(int n=1;n<=12;n++)chapters.add(new HakoParser.Link(bid+"-"+n,"Chương "+n,"https://docln.sbs/truyen/999990-cache/c"+n+"-lab"));
            store.catalog(bid,chapters);
            for(int n=1;n<=12;n++){
                String cid=bid+"-"+n;Store.write(store.html(cid),"<p>Đoạn văn trong kho dọn an toàn, giữ toàn bộ dấu tiếng Việt và vị trí đọc.</p>");
                store.state(cid,true,"");store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO read_chapters VALUES(?,?)",new Object[]{cid,System.currentTimeMillis()});
            }
            store.getWritableDatabase().execSQL("UPDATE books SET current=?,stamp=?,visits=1 WHERE id=?",new Object[]{bid+"-12",System.currentTimeMillis()-100L*86400000,bid});
            settings.edit().putInt("cache_retention_days",90).commit();ServerBridge.activeBook="";store.resetDerived();
            store.prune(store.book(bid));require(store.html(bid+"-1").isFile(),"Never delete the only copy without a server receipt");
            for(int n=1;n<=8;n++)uploads.edit().putString("ack_hash:"+bid+"-"+n,ContentUpload.hash(store.html(bid+"-"+n))).commit();
            settings.edit().putString("marks-"+bid,"[{\"chapter\":\""+bid+"-1\"}]").commit();
            store.prune(store.book(bid));require(store.html(bid+"-1").isFile(),"Bookmarks protect old text");settings.edit().remove("marks-"+bid).commit();
            store.setKeepFull(bid,true);store.prune(store.book(bid));require(store.html(bid+"-1").isFile(),"Explicit full downloads are retained");store.setKeepFull(bid,false);
            ServerBridge.activeBook=bid;store.prune(store.book(bid));require(store.html(bid+"-1").isFile(),"Active reader is protected");ServerBridge.activeBook="";
            store.getWritableDatabase().execSQL("DELETE FROM read_chapters WHERE id=?",new Object[]{bid+"-12"});store.resetDerived();
            store.prune(store.book(bid));require(store.html(bid+"-1").isFile(),"An unfinished book is protected");
            store.getWritableDatabase().execSQL("INSERT INTO read_chapters VALUES(?,?)",new Object[]{bid+"-12",System.currentTimeMillis()});store.resetDerived();
            uploads.edit().putString("pending:"+bid+"-1",bid).commit();store.prune(store.book(bid));
            require(store.html(bid+"-1").isFile(),"Pending file sync is protected");
            require(!store.html(bid+"-2").exists(),"Eligible old acknowledged text can expire");
            for(int n=9;n<=12;n++)require(store.html(bid+"-"+n).isFile(),"Current chapter and three preceding chapters remain");
            require(store.chapters(bid).size()==12&&store.book(bid).current.equals(bid+"-12")&&store.wasRead(bid+"-2"),"Cleanup keeps catalog, position and read history");
            return "only-copy; bookmark; full; active; unread; pending; age; anchors-and-history";
        }finally{
            ServerBridge.activeBook=active;settings.edit().putInt("cache_retention_days",days).remove("marks-"+bid).remove("keep_full_"+bid).commit();
            SharedPreferences.Editor edit=uploads.edit();for(String key:uploads.getAll().keySet())if(key.contains(bid))edit.remove(key);edit.commit();
            for(int n=1;n<=12;n++){Store.delete(store.dir(bid+"-"+n));store.getWritableDatabase().execSQL("DELETE FROM read_chapters WHERE id=?",new Object[]{bid+"-"+n});}
            store.getWritableDatabase().execSQL("DELETE FROM chapters WHERE book=?",new Object[]{bid});store.getWritableDatabase().execSQL("DELETE FROM books WHERE id=?",new Object[]{bid});store.resetDerived();LibraryState.refresh(c);
        }
    }
}
