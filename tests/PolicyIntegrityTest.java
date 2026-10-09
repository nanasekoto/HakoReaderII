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
    System.out.println("PolicyIntegrityTest passed");
  }
}
