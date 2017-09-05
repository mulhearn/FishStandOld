package edu.ucdavis.crayfis.fishstand;

import android.os.Handler;
import android.widget.Toast;

public class Log {
    private String logtxt = "";
    final private Runnable update;
    public String getTxt(){ return logtxt;}

    public Log(Runnable update){this.update = update;}

    public void append(String s){
        logtxt = logtxt + s;
        update.run();
    }
    public void clear(){
        //logtxt = "cleared\n";
        logtxt = "";
        update.run();
    }
}
