package edu.ucdavis.crayfis.fishstand;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

import edu.ucdavis.crayfis.fishstand.Analysis;

public class Demo implements Analysis {

    final int total        = 100;
    int requested;
    int processed;

    GridGeometry grid;
    final int margin = 300;
    final int cell_size = 100;


    public static Analysis newDemo(){
        Demo gain = new Demo();
        return gain;
    }

    private Demo() {
    }

    public void Init(){
    	requested=0;
	    processed=0;
    }

    public Boolean Next(CaptureRequest.Builder request){
        if (requested < total){
	    // CaptureRequest has been set as configured for ISO, eposure, etc already,
	    // but can be overriden here if, e.g, you want to scan exposure times.
            return true;
        } else {
            return false;
        }
    }
    public Boolean Done() {
        if (processed >= total) { return true; }
        else { return false; }	
    }

    public void ProcessImage(Image img) {
        Image.Plane iplane = img.getPlanes()[0];
        ByteBuffer buf = iplane.getBuffer();

        if (grid == null) {
            grid = new GridGeometry(img.getWidth(), img.getHeight(), margin, cell_size);
        }

        boolean trigger = false;

        int ncell = grid.getNumCells();
        int rs = iplane.getRowStride();
        int ps = iplane.getPixelStride();
        for (int icell = 0; icell < ncell; icell++) {
            int start = ps*grid.getRawCellStart(icell);
            short o = 0;
            for (int i = 0; i < 100; i++) {
                int istart = start + i * rs;
                for (int j = 0; j < 100; j++) {
                    short b = buf.getShort(istart + j * ps);
                    if (b > 0xff) {trigger = true; }
                    //o = (short) (((int) o) | ((int) b));
                }
            }
            int x = ((int) o) & ((int) 0xfff0);
            if (x != 0) trigger = true;
        }

        if (BkgWorker.getBkgWorker().daq.getState() != DaqWorker.State.STOPPED) {
            BkgWorker.getBkgWorker().daq.summary = "";
            BkgWorker.getBkgWorker().daq.summary += "on event " + processed + "\n";
            BkgWorker.getBkgWorker().daq.summary += "trigger:  " + trigger + "\n";
            BkgWorker.getBkgWorker().daq.update = true;
            processed = processed + 1;
        }
    }

    public void ProcessRun(){
        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();

        String filename = "demo_" + System.currentTimeMillis() + ".txt";
        File outfile = new File(path, filename);


        try {
            FileWriter writer = new FileWriter(outfile);
    	    writer.append("demo processed " + processed + " events.\n");
        } catch (IOException e) {
            BkgWorker.getBkgWorker().daq.summary += "ERROR opening txt file in Demo analysis.";
            e.printStackTrace();
        }
    }
}
