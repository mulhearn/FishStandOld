package edu.ucdavis.crayfis.fishstand;

import junit.framework.Assert;


public class AnalysisConfig {
    final App app;
    public AnalysisConfig(final App app){
        Assert.assertNotNull(app);
        this.app = app;
        chosen = Photo.newPhoto(app);
    }
    public Analysis chosen;
    public String[] Analyses = {"Photo","Gain","DarkNoise","HotCells"};
    public void select(int pos) {
        app.log.append("new analysis constructed.\n");
        if (pos==0) chosen = Photo.newPhoto(app);
        else if (pos==1) chosen = Gain.newGain(app);
        else if (pos==2) chosen = DarkNoise.newDarkNoise(app);
        else if (pos==3) chosen = HotCells.newHotCells(app);
    }

    //public Analysis getAnalysis(){ return photo; }
    public Analysis getAnalysis(){ return chosen; }
}
