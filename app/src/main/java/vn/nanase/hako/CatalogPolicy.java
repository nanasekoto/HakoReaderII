package vn.nanase.hako;
import java.util.*;
/** Web order wins; absent old chapters remain next to their old surviving successor. */
public final class CatalogPolicy {
  private CatalogPolicy(){}
  public static List<String> merge(List<String> previous,List<String> incoming){
    if(incoming==null||incoming.isEmpty())throw new IllegalArgumentException("Mục lục web trống");
    Set<String> seen=new HashSet<>();
    for(String id:incoming)if(id==null||id.isEmpty()||!seen.add(id))throw new IllegalArgumentException("ID chương rỗng hoặc trùng");
    List<String> result=new ArrayList<>(incoming);
    for(int i=0;i<previous.size();i++){
      String id=previous.get(i);
      if(seen.contains(id))continue;
      int insertion=result.size();
      for(int j=i+1;j<previous.size();j++)if(seen.contains(previous.get(j))){insertion=result.indexOf(previous.get(j));break;}
      result.add(insertion,id);
    }
    return result;
  }
}
