package vn.nanase.hako;
public final class BatchWindowTest {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){check(BatchWindow.start(-1,0,40)==0,"initial");for(int i=0;i<13;i++)check(BatchWindow.start(0,i,40)==0,"no sliding refill before fourteen");check(BatchWindow.start(0,13,40)==15,"fourteenth starts next batch");check(BatchWindow.start(15,14,40)==15,"fifteenth does not undo early refill");check(BatchWindow.start(15,27,40)==15,"second window stable");check(BatchWindow.start(15,28,40)==30,"second refill");check(BatchWindow.end(30,36)==36,"short tail");check(BatchWindow.start(0,25,40)==25,"far jump prepares chosen range");check(BatchWindow.start(30,2,40)==2,"back jump");check(BatchWindow.start(-1,0,0)==0,"empty");System.out.println("BatchWindowTest passed");}
}

