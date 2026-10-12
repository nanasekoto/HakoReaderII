package vn.nanase.hako;

import java.util.*;

/** Resume by identity, never by unread counts or a download/title hint. */
public final class ReadingNavigation {
  public static int resume(List<String> ids, String current, Set<String> read, Set<String> illustrations) {
    int saved=ids.indexOf(current);
    if(saved>=0&&!illustrations.contains(ids.get(saved)))return saved;
    int last=-1;
    for(int i=0;i<ids.size();i++)if(read.contains(ids.get(i))&&!illustrations.contains(ids.get(i)))last=i;
    if(last>=0){
      int next=adjacent(ids,last,1,illustrations);
      return next>=0?next:last;
    }
    return adjacent(ids,-1,1,illustrations);
  }
  public static int adjacent(List<String> ids,int current,int direction,Set<String> illustrations){
    if(direction!=1&&direction!=-1)return -1;
    for(int i=current+direction;i>=0&&i<ids.size();i+=direction)
      if(!illustrations.contains(ids.get(i)))return i;
    return -1;
  }
}
