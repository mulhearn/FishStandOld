package edu.ucdavis.crayfis.fishstand;

import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;
import android.text.InputType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;


public class CosmicsOld implements Analysis {
    final App app;

    // algorithm parameters:
    final int pixel_thresh   = 100;
    final int nbr_thresh     = 200;
    final int total          = 20000;
    final int margin = 300;
    final int cell_size = 100;
    final int zero_bias_prescale = 10000; // keep 1 cell per N
    final int hot_thresh = 2;

    // counts:
    int requested;
    int processed;
    long cells;

    GridGeometry grid;
    HotCellVeto  hotveto;

    CharHist maxhist; // max pixel in each cell
    CharHist avghist; // avg of all pixels in cell
    final Random rnd;
    CharHist rndhist; // a randomly chosen pixel from each cell
    CharHist clnhist; // max clean pixel in each cell
    CharHist nbrhist; // highest neigbor


    CharHist occhist; // occupancy of pixels above hot pixel threshold

    File trigfile;

    public static Analysis newCosmics(App app){
        CosmicsOld x = new CosmicsOld(app);
        return x;
    }

    private CosmicsOld(final App app) {
        this.app = app;
        rnd = new Random();
    }

    public void Init(){
        // TODO:  provide a proto-image to Init from DaqWorker.
    	requested=0;
	    processed=0;
        cells=0;
        maxhist = new CharHist((char) 1024, (char) 3);
        avghist = new CharHist((char) 1024, (char) 3);
        clnhist = new CharHist((char) 1024, (char) 3);
        nbrhist = new CharHist((char) 1024, (char) 3);
        occhist = new CharHist((char) 100, (char) 0);

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();
        String filename = "triggered_" + System.currentTimeMillis() + ".dat";
        trigfile = new File(path, filename);

        synchronized(this) {
            try {
                FileOutputStream writer = new FileOutputStream(trigfile);
                byte header[] = {(byte) 0xCF, (byte) 0xFF, (byte) 0x55, (byte) 0x77, (byte) 0x57, (byte) 0x75};
                writer.write(header);
                int val = -4403;
                writer.write(val&0xff);
                writer.write((val>>8)&0xff);
                writer.flush();
                writer.close();
            } catch (IOException e) {
                app.getDaq().log.append("ERROR opening txt file in CharHist.");
                e.printStackTrace();
            }
        }
        // Validate the GridGeometry class:
        //BkgWorker.getBkgWorker().daq.summary += GridGeometry.selfTest();
        //BkgWorker.getBkgWorker().daq.update = true;

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
        if (hotveto == null){
            int events = 100;
            if (total < 200) events = total / 2;
            hotveto = new HotCellVeto(events, hot_thresh, img.getHeight()*img.getWidth());
        }

        boolean calibrated = hotveto.isCalibrated();

        int ncell = grid.getNumCells();

        int nx = img.getWidth();
        int ps = iplane.getPixelStride();
        for (int icell = 0; icell < ncell; icell++) {
            boolean prescale = false;
            if ((cells % zero_bias_prescale) == 0) prescale = true;

            int start = grid.getRawCellStart(icell);
            short max = 0;
            short cmax = 0;
            int cmax_x = 0;
            int cmax_y = 0;

            int sum = 0;
            for (int x = 0; x < cell_size; x++) {
                for (int y = 0; y < cell_size; y++) {
                    int index = start + y * nx + x;
                    short b = buf.getShort(ps * index);
                    sum = sum + (int) b;
                    if (b > pixel_thresh) {
                        hotveto.addPixel(index);
                        if (b > max) max = b;
                        if (calibrated && (b > cmax) && (!hotveto.isHot(index))) {
                            cmax = b;
                            cmax_x = x;
                            cmax_y = y;
                        }
                    }
                }
                cells++;
            }
            int avg = (int) ((double) sum) / (cell_size * cell_size);
            if (maxhist != null) maxhist.increment(max);
            if ((calibrated) && (clnhist != null)) clnhist.increment(cmax);
            if (avghist != null) avghist.increment((short) avg);
            short nmax = 0;
            int nind = 0;
            if (cmax > pixel_thresh) {
                int[] ns = grid.neighbors(icell, cmax_x, cmax_y);
                for (int i = 0; i < ns.length; i++) {
                    int index = ns[i];
                    if (index < 0) continue;
                    if (hotveto.isHot(index)) continue;
                    short b = buf.getShort(ps * index);
                    if (b > nmax) {
                        nmax = b;
                        nind = index;
                    }
                }
                if ((calibrated) && (nbrhist != null)) nbrhist.increment(nmax);
            }
            if (calibrated && (prescale || (nmax > nbr_thresh))){
                synchronized(this){
                    try {
                        FileOutputStream writer = new FileOutputStream(trigfile, true);
                        writer.write((int) 0xcf);
                        writer.write((int) 0xee);
                        writer.write((int) icell&0xff);
                        writer.write((int) (icell>>8)&0xff);

                        if (prescale) writer.write((int) 0);
                        else          writer.write((int) 1);
                        writer.write((int) 0);
                        writer.write((int) cell_size);
                        writer.write((int) 0);
                        writer.write((int) cmax_x);
                        writer.write((int) 0);
                        writer.write((int) cmax_y);
                        writer.write((int) 0);
                        byte mlower = (byte) (nmax&0xff);
                        byte mupper = (byte) ((nmax>>8)&0xff);
                        writer.write(mlower);
                        writer.write(mupper);
                        writer.write(nind&0xff);
                        writer.write((nind>>8)&0xff);
                        writer.write((nind>>16)&0xff);
                        writer.write((nind>>24)&0xff);
                        for (int x = 0; x < cell_size; x++) {
                            for (int y = 0; y < cell_size; y++) {
                                int index = start + y * nx + x;
                                int b = (int) buf.getShort(ps * index);
                                if (hotveto.isHot(index)){ b = -b; }
                                byte lower = (byte) (b&0xff);
                                byte upper = (byte) ((b>>8)&0xff);
                                writer.write(lower);
                                writer.write(upper);
                            }
                        }
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        app.getDaq().log.append("ERROR opening txt file in CharHist.");
                        e.printStackTrace();
                    }
                }
            }
        }
        hotveto.addEvent();
        processed = processed + 1;
    }

    public void ProcessRun(){

        if (hotveto != null) {
            int hot[] = hotveto.hotCount();
            if (hot != null) {
                if (occhist != null) {
                    for (int i = 0; i < hot.length; i++) {
                        occhist.increment((short) hot[i]);
                    }
                }
            }
        }

        String postfix = "" + System.currentTimeMillis() + ".txt";

        if (maxhist != null) maxhist.writeToFile("hotcells_hmax_" + postfix);
        if (avghist != null) avghist.writeToFile("hotcells_havg_" + postfix);
        if (occhist != null) occhist.writeToFile("hotcells_hocc_" + postfix);
        if (clnhist != null) clnhist.writeToFile("hotcells_hcln_" + postfix);
        if (nbrhist != null) nbrhist.writeToFile("hotcells_hnbr_" + postfix);
    }
    public String getName(int iparam){return "";}
    public int    getType(int iparam){return InputType.TYPE_CLASS_NUMBER;} // or TYPE_CLASS_TEXT
    public String getParam(int iparam){ return ""; }
    public void   setParam(int iparam, String value) {}
}
