package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;





// a simple way to send update messages:

// produced new XXX data?
// -> Message.updateXXX();
// handle new XXX data?
//     BroadcastReceiver updater;
//   onCreateView():
//     updater = Message.onXXXUpdate(new Runnable(){public void run{...}});
//   onDestroyView():
//      Message.unregister(updater)

public class Message {
    // message types
    static final public String UPDATE_LOG   = "update-log";  // run results updates
    static final public String UPDATE_CAMERA   = "update-camera";  // camera config updates
    static final public String UPDATE_EXPOSURE = "update-exposure";  // camera config updates
    static final public String UPDATE_RESULT   = "update-results";  // run results updates

    static public void updateLog(){ send(UPDATE_LOG); }
    static public BroadcastReceiver onLogUpdate(Runnable r){ return onMessage(r, UPDATE_LOG); }

    static public void updateCamera(){ send(UPDATE_CAMERA); }
    static public BroadcastReceiver onCameraUpdate(Runnable r){ return onMessage(r, UPDATE_CAMERA); }

    static public void updateExposure(){ send(UPDATE_EXPOSURE); }
    static public BroadcastReceiver onExposureUpdate(Runnable r){ return onMessage(r, UPDATE_EXPOSURE); }

    static public void updateResult(){ send(UPDATE_RESULT); }
    static public BroadcastReceiver onResultUpdate(Runnable r){ return onMessage(r, UPDATE_RESULT); }

    static public void unregister(BroadcastReceiver r) {
        LocalBroadcastManager.getInstance(BkgWorker.getBkgWorker().getContext().getApplicationContext()).unregisterReceiver(r);
    }

    // generic versions:

    static public BroadcastReceiver onMessage(final Runnable r, final String action){
        IntentFilter filter = new IntentFilter(action);

        BroadcastReceiver receiver = new BroadcastReceiver(){
            public void onReceive(Context context, Intent intent){
                if (intent.getAction() == action) {
                    r.run();
                }
            }
        };
        LocalBroadcastManager.getInstance(BkgWorker.getBkgWorker().getContext().getApplicationContext()).registerReceiver(receiver,filter);
        return receiver;
    }

    static public void send(final String action) {
        LocalBroadcastManager.getInstance(BkgWorker.getBkgWorker().getContext().getApplicationContext()).sendBroadcast(new Intent(action));
    }

}

