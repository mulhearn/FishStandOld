package edu.ucdavis.crayfis.fishstand;

/**
 * Created by mulhearn on 8/30/17.
 */

public class UserSettings {
    App app;
    public UserSettings(App app){this.app = app;}

    // selected camera configuration: (in GUI, this is set in ExposureFragment):
    public int isens=0;
    public long exposure = 0;
    public long getExposure(){return exposure;}
    public int getSensitivity(){return app.getCamera().min_sens * (1 << isens);}
}
