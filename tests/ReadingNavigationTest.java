package vn.nanase.hako;
import java.util.*;
public final class ReadingNavigationTest {
  private static int checks;
  private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Reading check "+checks);}
  public static void main(String[] args){
    List<String> ids=Arrays.asList("art-1","text-1","caption","text-last","art-last");
    Set<String> art=new HashSet<>(Arrays.asList("art-1","art-last"));
    check(ReadingNavigation.resume(ids,"text-last",Collections.emptySet(),art)==3);
    check(ReadingNavigation.resume(ids,"caption",Collections.emptySet(),art)==2);
    check(ReadingNavigation.resume(ids,"",new HashSet<>(Arrays.asList("text-last","art-last")),art)==3);
    check(ReadingNavigation.resume(ids,"missing",Collections.singleton("text-last"),art)==3);
    check(ReadingNavigation.resume(ids,"",Collections.singleton("text-1"),art)==2);
    check(ReadingNavigation.resume(ids,"",Collections.emptySet(),art)==1);
    check(ReadingNavigation.adjacent(ids,3,1,art)==-1);
    check(ReadingNavigation.adjacent(ids,1,-1,art)==-1);
    check(ReadingNavigation.adjacent(ids,3,-1,art)==2);
    check(ReadingNavigation.resume(Collections.emptyList(),"",Collections.emptySet(),art)==-1);
    check(ReadingNavigation.resume(Arrays.asList("art-1","art-last"),"",Collections.emptySet(),art)==-1);
    System.out.println("PASS "+checks+" reading navigation checks");
  }
}
