package vn.nanase.hako;
import java.util.*;
public final class ShelfAnchor {
 // Stop at a known latest chapter, independently of whether it has been read.
 public static boolean canStopAtUnchanged(List<Row> rows,Map<String,String> before,boolean enabled){
  if(!enabled||rows.isEmpty())return false;
  boolean found=false;
  for(Row row:rows){
   if(row.key==null||row.key.isEmpty())return false;
   boolean same=row.key.equals(before.get(row.id));
   if(found&&!same)return false;
   if(same)found=true;
  }
  return found;
 }
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
