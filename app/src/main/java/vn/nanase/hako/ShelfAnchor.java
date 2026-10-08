package vn.nanase.hako;
import java.util.*;
public final class ShelfAnchor {
 public static final class Row {public final String id,key;public final int unread;public Row(String i,String k,int u){id=i;key=k;unread=u;}}
 public static boolean canStop(List<Row> rows,String anchor,Map<String,String> before,boolean enabled){
  if(!enabled||anchor==null||anchor.isEmpty()||rows.isEmpty())return false;
  boolean found=false;
  for(Row r:rows){
   if(r.key==null||r.key.isEmpty())return false;
   boolean unchanged=r.key.equals(before.get(r.id));
   if(r.id.equals(anchor)){if(r.unread<=0||!unchanged)return false;found=true;}
   if(found&&!unchanged)return false;
  }
  return found;
 }
}
