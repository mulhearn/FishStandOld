package edu.ucdavis.crayfis.fishstand;


public class UserSettings {
    final private App app;

    public UserSettings(App app) {
        this.app = app;
    }

    // selected camera configuration: (in GUI, this is set in ExposureFragment):
    public int sens = 0;
    public long exposure = 0;
    // these are run settings:
    public int delay = 0;
    public boolean auto_rerun = false;

    public long getExposure() {
        return exposure;
    }

    public int getSensitivity() {
        return sens;
    }
}
