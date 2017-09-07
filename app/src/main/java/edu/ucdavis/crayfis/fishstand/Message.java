package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

// a simple way to send update messages between GUI and asynchronous worker threads

// produced new XXX data?
// -> Message.updateXXX();
// handle new XXX data?
//     BroadcastReceiver updater;
//   onCreateView():
//     updater = Message.onXXXUpdate(new Runnable(){public void run{...}});
//   onDestroyView():
//      Message.unregister(updater)

public class Message {
    final private Context context;
    Message(Context context){ this.context = context; }

    // message types
    // these can be made as fine as possible, for now using fairly coarse categories:
    static final public String UPDATE_LOG     = "update-log";             // update a log file
    static final public String UPDATE_SETTING = "update-user-setting";    // update a setting or parameter
    static final public String UPDATE_RESULT  = "update-exposure-result"; // update a result

    public void updateLog(){ send(UPDATE_LOG); }
    public BroadcastReceiver onLogUpdate(Runnable r){ return onMessage(r, UPDATE_LOG); }

    public void updateSetting(){ send(UPDATE_SETTING); }
    public BroadcastReceiver onSettingUpdate(Runnable r){ return onMessage(r, UPDATE_SETTING); }

    public void updateResult(){ send(UPDATE_RESULT); }
    public BroadcastReceiver onResultUpdate(Runnable r){ return onMessage(r, UPDATE_RESULT); }

    public void unregister(BroadcastReceiver r) {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(r);
    }

    // generic versions:

    public BroadcastReceiver onMessage(final Runnable r, final String action){
        IntentFilter filter = new IntentFilter(action);

        BroadcastReceiver receiver = new BroadcastReceiver(){
            public void onReceive(Context context, Intent intent){
                if (intent.getAction() == action) {
                    r.run();
                }
            }
        };
        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(receiver,filter);
        return receiver;
    }

    public void send(final String action) {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(new Intent(action));
    }

}

