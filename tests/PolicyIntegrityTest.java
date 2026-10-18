package vn.nanase.hako;
import java.util.*;
public final class PolicyIntegrityTest {
  private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  private static Set<String> set(String...ids){return new HashSet<>(Arrays.asList(ids));}
  public static void main(String[] args){
    List<String> merged=CatalogPolicy.merge(Arrays.asList("a","hidden","b","c"),Arrays.asList("a","new","b","c"));
    check(merged.equals(Arrays.asList("a","new","hidden","b","c")),"Preserve hidden chapter and insert new in web order");
    check(new HashSet<>(merged).size()==merged.size(),"Unique chapter positions");
    check(CatalogPolicy.merge(Arrays.asList("a","b","c"),Arrays.asList("c","a")).equals(Arrays.asList("b","c","a")),"Respect reordered web chapters");
    boolean rejected=false;try{CatalogPolicy.merge(Arrays.asList("a"),Collections.emptyList());}catch(IllegalArgumentException e){rejected=true;}
    check(rejected,"Empty web catalog rejected");
    rejected=false;try{CatalogPolicy.merge(Arrays.asList("a"),Arrays.asList("a","a"));}catch(IllegalArgumentException e){rejected=true;}
    check(rejected,"Duplicate web catalog rejected");
    check(!ReadPolicy.canSubmit(Collections.emptyList(),set("a"),set("a"),true),"Empty server cannot mark all");
    check(!ReadPolicy.canSubmit(Arrays.asList("a","c"),set("a","b"),set("a","b"),true),"Same count different ID blocked");
    check(!ReadPolicy.canSubmit(Arrays.asList("a","b"),set("a","b"),set("a"),true),"New chapter after offline completion blocked");
    check(!ReadPolicy.canSubmit(Arrays.asList("a"),set("a"),set("a"),false),"Unverified catalog blocked");
    check(ReadPolicy.canSubmit(Arrays.asList("a","b"),set("a","b"),set("a","b"),true),"Verified read IDs can submit");
    check(ReadPolicy.verifySubmission(Arrays.asList("main","side"),set("main","side"),set("main","side"),"main","main",true).equals("OK"),"Read side story after main tip does not block completion");
    check(ReadPolicy.verifySubmission(Arrays.asList("main","side"),set("main"),set("main","side"),"main","main",true).equals("NEW_UNREAD"),"Unread side story remains protected");
    check(ReadPolicy.verifySubmission(Arrays.asList("main","inserted","side"),set("main","inserted","side"),set("main","side"),"side","side",true).equals("SNAPSHOT_MISSING_IDS"),"Inserted text absent from completion snapshot blocks submission");
    check(ReadPolicy.verifySubmission(Arrays.asList("main","side"),set("main","side"),set("main","side"),"main","side",true).equals("TIP_MISMATCH"),"Different request and completion tips are reported explicitly");
    check(ReadPolicy.verifySubmission(Arrays.asList("main","main"),set("main"),set("main"),"main","main",true).equals("INVALID_SERVER_IDS"),"Duplicate IDs are rejected with specific reason");
    check(ReadPolicy.verifySubmission(Collections.emptyList(),set("main"),set("main"),"main","main",true).equals("EMPTY_SERVER_TEXT"),"Empty text list cannot claim completion");
    check(ReadPolicy.verifySubmission(Arrays.asList("main"),set("main"),set("main"),"main","main",false).equals("CATALOG_FAILED"),"Fresh verification is still mandatory");
    check(ReadPolicy.verifySubmission(Arrays.asList("main"),set("main"),set("main"),"missing","missing",true).equals("TIP_NOT_READ"),"Queue tip must have read evidence");
    System.out.println("PolicyIntegrityTest passed");
    check(ReadPolicy.canSubmitAtTip(Arrays.asList("a","b"),"b","b",true),"Unchanged latest text can send queued completion");
    check(!ReadPolicy.canSubmitAtTip(Arrays.asList("a","b","c"),"b","b",true),"Newer text blocks queued completion");
    check(!ReadPolicy.canSubmitAtTip(Arrays.asList("a","b"),"a","b",true),"Old queued tip cannot claim newer completion");
    check(!ReadPolicy.canSubmitAtTip(Arrays.asList("a","a"),"a","a",true),"Duplicate server IDs blocked");
    check(!ReadPolicy.canSubmitAtTip(Arrays.asList("a"),"a","a",false),"Failed server verification blocked");
  }
}
