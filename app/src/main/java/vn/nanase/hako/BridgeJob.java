package vn.nanase.hako;

import android.app.job.*;
import android.content.*;
import java.util.Map;

/** Network-constrained retry of durable changes; no idle polling or web scraping. */
public final class BridgeJob extends JobService {
    static final int ID=61;
    static boolean pending(Context c){
        for(Map.Entry<String,?> item:ServerBridge.prefs(c).getAll().entrySet())
            if(item.getKey().startsWith("pending:")||item.getKey().startsWith("flags:")&&Boolean.TRUE.equals(item.getValue()))return true;
        return false;
    }
    static void schedule(Context c){
        if(!ServerBridge.enabled(c)||!pending(c))return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(scheduler!=null&&scheduler.getPendingJob(ID)==null)scheduler.schedule(
            new JobInfo.Builder(ID,new ComponentName(c,BridgeJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(DeviceProfile.eink(c)?60000:3000).setPersisted(true)
                .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public boolean onStartJob(JobParameters params){
        if(!ServerBridge.enabled(this)||!pending(this))return false;
        ServerBridge.worker.execute(()->{ServerBridge.sync(this);jobFinished(params,ServerBridge.enabled(this)&&pending(this));});
        return true;
    }
    public boolean onStopJob(JobParameters params){return true;}
}
