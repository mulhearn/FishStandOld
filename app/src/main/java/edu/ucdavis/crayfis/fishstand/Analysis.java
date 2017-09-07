package edu.ucdavis.crayfis.fishstand;


import android.hardware.camera2.CaptureRequest;
import android.media.Image;

public interface Analysis {
    // UI interface:
    //public String[] parameters();
    //public void handleParameter(int pos, int val);

    // DAQ interface:
    public void Init();
    public Boolean Next(CaptureRequest.Builder request);
    public Boolean Done();
    public void ProcessImage(Image img);
    public void ProcessRun();

    // Parameter interface:
    public String getName(int iparam);
    public int    getType(int iparam);
    public String getParam(int iparam);
    public void   setParam(int iparam, String value);

}

