package vn.nanase.hako;
import java.util.HashSet;
import java.util.Set;
public final class DownloadLedger {
 private final Set<String> queued=new HashSet<>(), completed=new HashSet<>();
 public void add(String id){queued.add(id);}
 public void complete(String id){if(queued.contains(id))completed.add(id);}
 public int total(){return queued.size();}
 public int done(){return completed.size();}
 public static int percent(int done,int total){if(total<=0)return 0;return done>=total?100:Math.min(99,(int)((long)Math.max(0,done)*100/total));}
 public void clear(){queued.clear();completed.clear();}
}

