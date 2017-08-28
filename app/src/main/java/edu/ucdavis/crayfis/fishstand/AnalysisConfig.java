package edu.ucdavis.crayfis.fishstand;

import static android.content.ContentValues.TAG;
import edu.ucdavis.crayfis.fishstand.Analysis;
import edu.ucdavis.crayfis.fishstand.Photo;
import edu.ucdavis.crayfis.fishstand.Gain;


public class AnalysisConfig {
    Analysis chosen     = Photo.newPhoto();


    public String[] Analyses = {"Photo","Gain","DarkNoise","HotCells"};
    public void select(int pos) {
        if (pos==0) chosen = Photo.newPhoto();
        else if (pos==1) chosen = Gain.newGain();
        else if (pos==2) chosen = DarkNoise.newDarkNoise();
        else if (pos==3) chosen = HotCells.newHotCells();
    }

    //public Analysis getAnalysis(){ return photo; }
    public Analysis getAnalysis(){ return chosen; }
}
