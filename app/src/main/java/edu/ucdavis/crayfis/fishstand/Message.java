package edu.ucdavis.crayfis.fishstand;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
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
    private Context context;
    Message(Context context){ this.context = context; }

    // message types
    static final public String UPDATE_LOG = "update-log";  // app-wide log file updates
    static final public String UPDATE_DAQ_SUMMARY = "update-daq-summary";  // daq summary updates
    static final public String UPDATE_CAMERA_SUMMARY = "update-camera-summary";  // camera summary updates
    static final public String UPDATE_EXPOSURE_SETTINGS = "update-exposure-settings";  // camera exposure settings updates
    static final public String UPDATE_EXPOSURE_RESULTS = "update-exposure-results";  // exposure check results updates

    public void updateLog(){ send(UPDATE_LOG); }
    public BroadcastReceiver onLogUpdate(Runnable r){ return onMessage(r, UPDATE_LOG); }

    public void updateDaqSummary(){ send(UPDATE_DAQ_SUMMARY); }
    public BroadcastReceiver onDaqSummaryUpdate(Runnable r){ return onMessage(r, UPDATE_DAQ_SUMMARY); }

    public void updateCameraSummary(){ send(UPDATE_CAMERA_SUMMARY); }
    public BroadcastReceiver onCameraSummaryUpdate(Runnable r){ return onMessage(r, UPDATE_CAMERA_SUMMARY); }

    public void updateExposureSettings(){ send(UPDATE_EXPOSURE_SETTINGS); }
    public BroadcastReceiver onExposureSettingsUpdate(Runnable r){ return onMessage(r, UPDATE_EXPOSURE_SETTINGS); }

    public void updateExposureResults(){ send(UPDATE_EXPOSURE_RESULTS); }
    public BroadcastReceiver onExposureResultsUpdate(Runnable r){ return onMessage(r, UPDATE_EXPOSURE_RESULTS); }


    final public String UPDATE_RESULT   = "update-results";  // run results updates
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

