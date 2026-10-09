package vn.nanase.hako;
import java.util.*;
/** An offline completion snapshot must cover every freshly verified server ID. */
public final class ReadPolicy {
  private ReadPolicy(){}
  public static boolean canSubmit(Collection<String> server,Set<String> read,Set<String> snapshot,boolean verified){
    if(!verified||server==null||server.isEmpty()||read==null||snapshot==null)return false;
    Set<String> seen=new HashSet<>();
    for(String id:server)if(id==null||id.isEmpty()||!seen.add(id)||!read.contains(id)||!snapshot.contains(id))return false;
    return true;
  }
}
