package vn.nanase.hako;
import java.util.*;
/** An offline completion snapshot must cover every freshly verified server ID. */
public final class ReadPolicy {
  private ReadPolicy(){}
  public static boolean canSubmitAtTip(List<String> textIds,String queuedTip,String completedTip,boolean verified){
    if(!verified||textIds==null||textIds.isEmpty()||queuedTip==null||!queuedTip.equals(completedTip))return false;
    Set<String> unique=new HashSet<>();for(String id:textIds)if(id==null||id.isEmpty()||!unique.add(id))return false;
    return queuedTip.equals(textIds.get(textIds.size()-1));
  }
  /** Completion is ID coverage, not the visual position of a chapter in a volume. */
  public static String verifySubmission(Collection<String> server,Set<String> read,Set<String> snapshot,String queuedTip,String completedTip,boolean verified){
    if(!verified)return "CATALOG_FAILED";
    if(server==null||server.isEmpty())return "EMPTY_SERVER_TEXT";
    Set<String> seen=new HashSet<>();for(String id:server)if(id==null||id.isEmpty()||!seen.add(id))return "INVALID_SERVER_IDS";
    if(queuedTip==null||queuedTip.isEmpty()||!queuedTip.equals(completedTip))return "TIP_MISMATCH";
    if(read==null||!read.contains(queuedTip))return "TIP_NOT_READ";
    if(snapshot==null||!snapshot.contains(queuedTip))return "TIP_NOT_IN_SNAPSHOT";
    for(String id:server)if(!read.contains(id))return "NEW_UNREAD";
    for(String id:server)if(!snapshot.contains(id))return "SNAPSHOT_MISSING_IDS";
    return "OK";
  }
  public static boolean canSubmit(Collection<String> server,Set<String> read,Set<String> snapshot,boolean verified){
    if(!verified||server==null||server.isEmpty()||read==null||snapshot==null)return false;
    Set<String> seen=new HashSet<>();
    for(String id:server)if(id==null||id.isEmpty()||!seen.add(id)||!read.contains(id)||!snapshot.contains(id))return false;
    return true;
  }
}
