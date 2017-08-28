package edu.ucdavis.crayfis.fishstand;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class BkgWorker implements Runnable {

    // maintain a simple log within the App:

    private String log = "";

    static public void appendLog(String s){
        lonelyInstance.log = lonelyInstance.log + s;
        Message.updateLog();
    }

    static public void clearLog(){

        lonelyInstance.log = "log cleared\n";
        Message.updateLog();
    }

    static public String getLog(){
        return lonelyInstance.log;
    }

    public AnalysisConfig analysis;
    public CameraConfig camera;
    public DaqWorker daq;
    public ExposureCheck exposure;

    // Save the context and UI thread handler, for interactions with UI.
    final private Context context;
    final private Handler uihandler;

    public Context getContext(){ return context; }
    public Handler getUiHandler(){ return uihandler; }

    // A background handler, for any non-UI responses    
    private Handler bkghandler = null;
    public Handler getBkgHandler(){ return bkghandler; }

    private BkgWorker(Context context, Handler uihandler){
	    this.context = context;
	    this.uihandler = uihandler;
        log = "FishStand started.\n";
    }

    private static BkgWorker lonelyInstance = null;
    public static BkgWorker getBkgWorker(){ return lonelyInstance; }
    public static void startBkgWorker(Context context, Handler uihandler) {
        if (lonelyInstance == null) {
            lonelyInstance = new BkgWorker(context, uihandler);
            lonelyInstance.camera = new CameraConfig();
            lonelyInstance.daq = new DaqWorker();
            lonelyInstance.analysis = new AnalysisConfig();
            lonelyInstance.exposure = new ExposureCheck();
            new Thread(lonelyInstance).start();
        }
    }

    // Sent toasts on the UI thread:
    public void short_toast(final String msg){
        uihandler.post(new Runnable() {
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void long_toast(final String msg){
        uihandler.post(new Runnable() {
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void run(){
        Looper.prepare();
        bkghandler = new Handler();
        camera.Init();

        Looper.loop();
    }

};
