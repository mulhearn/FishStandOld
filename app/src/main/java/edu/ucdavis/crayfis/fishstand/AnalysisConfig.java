package edu.ucdavis.crayfis.fishstand;

import junit.framework.Assert;


public class AnalysisConfig {
    final App app;
    private int pos;
    public AnalysisConfig(final App app){
        Assert.assertNotNull(app);
        this.app = app;
        pos = 0;
        chosen = Photo.newPhoto(app);
    }
    public Analysis chosen;
    public String[] Analyses = {"Photo","Gain","Shading","Noise","Cosmics"};
    public void select(int pos) {
        if (app.getDaq().getState() != DaqWorker.State.STOPPED) {
            app.log.append("Attempt to change analysis when not STOPPED ignored.\n");
            return;
        }
        if (pos != this.pos) {
            this.pos = pos;
            app.log.append("New analysis constructed at position " + pos + ".\n");
            if (pos == 0) chosen = Photo.newPhoto(app);
            else if (pos == 1) chosen = Gain.newGain(app);
            else if (pos == 2) chosen = Shading.newShading(app);
            else if (pos == 3) chosen = Noise.newHotCells(app);
            else if (pos == 4) chosen = Cosmics.newCosmics(app);
            app.getMessage().updateSetting();
        } else {
            app.log.append("Attempt to reselect same analysis ignored.\n");
            return;
        }
    }
    //public Analysis getAnalysis(){ return photo; }
    public Analysis getAnalysis(){ return chosen; }
    public int getPosition(){ return pos; }

}
