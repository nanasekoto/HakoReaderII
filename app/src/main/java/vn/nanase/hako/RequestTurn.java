package vn.nanase.hako;
/** One network owner. Foreground waiters run before the next background request. */
public final class RequestTurn {
 public static final ThreadLocal<Boolean> background=ThreadLocal.withInitial(()->false);
 private boolean active;private int foreground;
 public synchronized void acquire(boolean low)throws InterruptedException{if(!low)foreground++;try{while(active||(low&&foreground>0))wait();active=true;}finally{if(!low)foreground--;notifyAll();}}
 public synchronized void release(){active=false;notifyAll();}
 public synchronized boolean active(){return active;}
 public synchronized int waitingForeground(){return foreground;}
}

