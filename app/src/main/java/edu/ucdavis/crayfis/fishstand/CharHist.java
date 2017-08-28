package edu.ucdavis.crayfis.fishstand;

import android.os.Environment;

import com.jjoe64.graphview.series.DataPoint;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

// a lightweight, character histogram
public class CharHist {
    private long data[];
    private char shift;
    private int nbins;

    public long[] getData(){return data;}

    public CharHist(char max, char shift){
        nbins = 1+(int) max>>shift;
        data = new long[nbins];
        this.shift = shift;
    }
    public void increment(char i){
        int ibin = (i >> shift);
        if (ibin >= nbins) {
            //BkgWorker.appendLog("max:  " + (int) i + " to " + ibin + " likely from " + debug_short + "\n");
            ibin = nbins-1;
        }
        data[ibin]++;
    }

    //public short debug_short = 0;
    public void increment(short i){
        //debug_short = i;
        //strip off the minus sign and increment as a char:
        char b = (char) (i&0x7fff);
        increment(b);
    }

    public DataPoint[] asDataPoints(){
        DataPoint d[] = new DataPoint[nbins];
        for (int i=0; i<nbins; i++){
            d[i] = new DataPoint((i<<shift),data[i]);
        }
        return d;
    }

    public void writeToFile(String filename){
        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();

        File outfile = new File(path, filename);

        try {
            FileWriter writer = new FileWriter(outfile);

            writer.append("0");
            for (int i=1; i< nbins; i++) {
                writer.append(", " + (i<<shift));
            }
            writer.append("\n");

            writer.append("" +data[0]);
            for (int i=1; i< nbins; i++) {
                writer.append(", " + data[i]);
            }
            writer.append("\n");
            writer.flush();
            writer.close();

        } catch (IOException e) {
            BkgWorker.getBkgWorker().daq.summary += "ERROR opening txt file in CharHist.";
            e.printStackTrace();
        }

    }

}
