package edu.ucdavis.crayfis.fishstand;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public class App implements Runnable {
    public Log log;
    private Message message;
    private CameraConfig camera;
    private DaqWorker daq;
    private UserSettings settings;
    private AnalysisConfig analysis;
    // Save the context and UI thread handler, for interactions with UI.
    final private Context context;
    final private Handler uihandler;
    // A background handler, for any non-UI tasks
    private Handler bkghandler = null;


    public Message getMessage(){return message;}
    public DaqWorker getDaq(){return daq;}
    public CameraConfig getCamera(){return camera;}
    public UserSettings getSettings(){return settings;}
    public Handler getBkgHandler(){ return bkghandler; }
    public AnalysisConfig getAnalysisConfig(){ return analysis; }
    public Analysis getChosenAnalysis(){ return analysis.chosen; }
    public Context getContext(){ return context; }
    public Handler getUiHandler(){ return uihandler; }

    public App(Context context, Handler uihandler){
	    this.context = context;
	    this.uihandler = uihandler;
        message  = new Message(context);
        camera   = new CameraConfig(this);
        daq      = new DaqWorker(this);
        analysis = new AnalysisConfig(this);
        settings = new UserSettings(this);
        new Thread(this).start();
        log      = new Log(new Runnable() {public void run(){getMessage().updateLog();}});
        log.append("Fish Stand started on " + DateFormat.getDateTimeInstance().format(new Date(System.currentTimeMillis())) + "\n");
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
