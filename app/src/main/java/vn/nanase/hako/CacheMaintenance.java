package vn.nanase.hako;

import android.content.*;
import java.io.File;
import java.util.*;

/** Optional daily charging maintenance, outside lists, page turns and UI locks. */
final class CacheMaintenance {
    static long bytes(File root){long total=0;File[] files=root.listFiles();if(files!=null)for(File file:files)total+=file.isDirectory()?bytes(file):file.length();return total;}
    static void run(Context c){
        SharedPreferences p=c.getSharedPreferences("settings",0);
        int days=p.getInt("cache_retention_days",0),budget=p.getInt("cache_budget_mb",0);
        if((days<=0&&budget<=0)||!ChargeJob.isCharging(c)||!ReadSync.wifi(c)||!ServerBridge.activeBook.isEmpty())return;
        long now=System.currentTimeMillis();if(now-p.getLong("cache_maintenance_at",0)<86400000L)return;
        Store store=Store.get(c);long total=bytes(store.cache),limit=budget<=0?Long.MAX_VALUE:budget*1024L*1024L;
        List<Store.Book> books=store.books();books.sort(Comparator.comparingLong(b->b.stamp));
        for(Store.Book book:books){
            if(!ServerBridge.activeBook.isEmpty()||!ChargeJob.isCharging(c))return;
            long before=0;for(Store.Chapter chapter:store.chapters(book.id))before+=bytes(store.dir(chapter.id));
            store.prune(book,total>limit);
            long after=0;for(Store.Chapter chapter:store.chapters(book.id))after+=bytes(store.dir(chapter.id));
            total=Math.max(0,total-before+after);
        }
        p.edit().putLong("cache_maintenance_at",now).putLong("cache_bytes",total)
            .putString("cache_maintenance_status",total>limit?"Giữ dữ liệu được bảo vệ; có thể vượt ngân sách":"Đã kiểm tra cache an toàn").apply();
    }
}
