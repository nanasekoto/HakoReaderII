package vn.nanase.hako;

import android.content.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;

/** Real Android outbox/file code over an in-memory disconnected transport. */
public final class FixtureUploadProbe {
    static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:values.entrySet()){
        Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);
    }edit.commit();}
    public static String run(Context c)throws Exception{
        FixtureLibrary.requireLab(c);SharedPreferences bridge=ServerBridge.prefs(c),outbox=ContentUpload.prefs(c);
        Map<String,?> previous=bridge.getAll(),oldOutbox=outbox.getAll();ContentUpload.Transport transport=ContentUpload.transport;
        String active=ServerBridge.activeBook;String bid="truyen-999910",cid=bid+"-2";
        final int[] calls={0},chunks={0},begins={0};final boolean[] fail={true},accepted={false};
        ArrayList<ByteArrayOutputStream> received=new ArrayList<>();final JSONArray[] files={null};
        try{
            bridge.edit().clear().putString("server","https://example.invalid/lab").putString("token","fixture-only-never-a-real-token").commit();outbox.edit().clear().commit();
            SharedPreferences.Editor queue=outbox.edit().putString("pending:"+cid,bid);
            for(int n=0;n<7;n++)queue.putString("pending:truyen-999919-"+n,"truyen-999919");queue.commit();
            ServerBridge.activeBook="";ContentUpload.stopped=false;
            ContentUpload.transport=new ContentUpload.Transport(){public boolean connected(Context ignored){return true;}public JSONObject post(Context ctx,String path,JSONObject value)throws Exception{
                calls[0]++;
                if(path.equals("/v3/pocket/metadata"))return new JSONObject().put("accepted",true);
                if(path.endsWith("/begin")){
                    begins[0]++;require(cid.equals(value.getString("chapter")),"Latest chapter gets the first file slot");
                    if(files[0]==null){files[0]=value.getJSONArray("files");for(int i=0;i<files[0].length();i++)received.add(new ByteArrayOutputStream());}
                    if(accepted[0])return new JSONObject().put("session","fixture-session").put("accepted",true).put("chapter",cid);
                    JSONArray offsets=new JSONArray();for(ByteArrayOutputStream file:received)offsets.put(file.size());
                    return new JSONObject().put("session","fixture-session").put("offsets",offsets);
                }
                if(path.endsWith("/chunk")){
                    int index=value.getInt("index");ByteArrayOutputStream file=received.get(index);
                    require(value.getInt("offset")==file.size(),"Retry starts at acknowledged byte offset");
                    file.write(Base64.decode(value.getString("data"),Base64.DEFAULT));chunks[0]++;
                    return new JSONObject().put("offset",file.size());
                }
                if(path.endsWith("/commit")){
                    if(fail[0]){fail[0]=false;throw new IOException("Fixture disconnect before ACK");}
                    for(int i=0;i<received.size();i++)require(ContentUpload.hash(received.get(i).toByteArray()).equals(files[0].getJSONObject(i).getString("sha256")),"Transferred bytes match the manifest");
                    accepted[0]=true;return new JSONObject().put("session","fixture-session").put("accepted",true).put("chapter",cid);
                }
                throw new AssertionError("Unexpected transport path "+path);
            }};
            // Pending position must block all file traffic, not just lower its priority.
            bridge.edit().putString("pending:"+bid,"fixture-position").commit();
            try{ContentUpload.drain(c);}catch(IOException expected){}
            require(calls[0]==0,"Unsent position has absolute priority");bridge.edit().remove("pending:"+bid).commit();
            try{ContentUpload.drain(c);throw new AssertionError("Simulated disconnect must fail");}catch(IOException expected){}
            require(outbox.contains("pending:"+cid)&&!outbox.contains("ack:"+cid),"Disconnect retains durable queue without false acknowledgement");
            int firstChunks=chunks[0];ContentUpload.drain(c);
            require(accepted[0]&&!outbox.contains("pending:"+cid)&&outbox.contains("ack_hash:"+cid),"Actual commit acknowledgement clears the queue");
            require(chunks[0]==firstChunks&&begins[0]==2,"Resume reuses uploaded file bytes instead of sending again");
            require(Store.get(c).readable(cid),"Upload never deletes the local readable chapter");
            // A new exit with unchanged files is acknowledged locally without HTTP.
            outbox.edit().putString("pending:"+cid,bid).commit();int before=calls[0];
            for(int n=0;n<7;n++)outbox.edit().putString("pending:truyen-999919-"+n,"truyen-999919").commit();
            ContentUpload.drain(c);require(calls[0]==before,"Unchanged acknowledged files produce no network traffic");
            return "position-first; durable-disconnect; resume-without-reupload; hash-verified-ACK; local-file-retained; no-op-exit";
        }finally{
            ContentUpload.transport=transport;ServerBridge.activeBook=active;restore(bridge,previous);restore(outbox,oldOutbox);
            c.getSystemService(android.app.job.JobScheduler.class).cancel(ContentUpload.JOB);
        }
    }
}
