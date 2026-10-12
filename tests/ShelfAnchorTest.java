package vn.nanase.hako;
import java.util.*;
public final class ShelfAnchorTest {
 static ShelfAnchor.Row r(String id,String key,int unread){return new ShelfAnchor.Row(id,key,unread);}
 public static void main(String[] args){
  Map<String,String> old=new HashMap<>();old.put("d","d100");old.put("e","e10");
  if(!ShelfAnchor.canStop(Arrays.asList(r("new","n1",1),r("d","d100",3),r("e","e10",2)),"e",old,true))throw new AssertionError("Oldest unread anchor should stop after new rows");
  if(ShelfAnchor.canStop(Arrays.asList(r("e","e11",3)),"e",old,true))throw new AssertionError("Anchor with new chapter must continue");
  if(ShelfAnchor.canStop(Arrays.asList(r("e","e10",0)),"e",old,true))throw new AssertionError("Read marker moved must continue");
  if(ShelfAnchor.canStop(Arrays.asList(r("e","",2)),"e",old,true))throw new AssertionError("Unknown metadata must continue");
  if(ShelfAnchor.canStop(Arrays.asList(r("e","e10",2)),"e",old,false))throw new AssertionError("Initial/unsafe ordering must continue");
  if(ShelfAnchor.canStop(Arrays.asList(r("e","e10",2),r("later","new",1)),"e",old,true))throw new AssertionError("Changed row after anchor must continue");
  if(ShelfAnchor.canStop(Arrays.asList(r("d","d100",2)),"e",old,true))throw new AssertionError("Missing anchor must continue");
  System.out.println("Shelf anchor regression scenarios passed");
  if(!ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("new1","x9",1),r("new2","y8",2),r("d","d100",3)),old,true))throw new AssertionError("Unchanged latest key at row 3 stops later pages");
  if(!ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("d","d100",0)),old,true))throw new AssertionError("Read count does not affect latest chapter anchor");
  if(ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("d","d101",3)),old,true))throw new AssertionError("New chapter must continue");
  if(ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("d","d100",3),r("e","e11",2)),old,true))throw new AssertionError("Changed row after anchor is unsafe");
  if(ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("d","d100",3)),old,false))throw new AssertionError("Unsafe sorting must continue");
  if(ShelfAnchor.canStopAtUnchanged(Arrays.asList(r("d","",3)),old,true))throw new AssertionError("Unknown latest key cannot stop");
  System.out.println("Snapshot shelf anchor scenarios passed");
 }
}
