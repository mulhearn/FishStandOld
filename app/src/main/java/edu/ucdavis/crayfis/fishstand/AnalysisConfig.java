package edu.ucdavis.crayfis.fishstand;

import static android.content.ContentValues.TAG;
import edu.ucdavis.crayfis.fishstand.Analysis;
import edu.ucdavis.crayfis.fishstand.Photo;
import edu.ucdavis.crayfis.fishstand.Gain;


public class AnalysisConfig {
    App app;
    public AnalysisConfig(App app){
        this.app = app;
    }
    public Analysis chosen     = Photo.newPhoto(app);

    // analysis parameters:
    public int delay=0;


    public String[] Analyses = {"Photo","Gain","DarkNoise","HotCells"};
    public void select(int pos) {
        if (pos==0) chosen = Photo.newPhoto(app);
        else if (pos==1) chosen = Gain.newGain(app);
        else if (pos==2) chosen = DarkNoise.newDarkNoise(app);
        else if (pos==3) chosen = HotCells.newHotCells(app);
    }

    //public Analysis getAnalysis(){ return photo; }
    public Analysis getAnalysis(){ return chosen; }
}
