package vn.nanase.hako;
import java.util.HashSet;
import java.util.Set;
public final class DownloadLedger {
 private final Set<String> queued=new HashSet<>(), completed=new HashSet<>();
 public void add(String id){queued.add(id);}
 public void complete(String id){if(queued.contains(id))completed.add(id);}
 public int total(){return queued.size();}
 public int done(){return completed.size();}
 public void clear(){queued.clear();completed.clear();}
}
