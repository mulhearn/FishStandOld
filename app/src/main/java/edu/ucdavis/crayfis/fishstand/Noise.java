package edu.ucdavis.crayfis.fishstand;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;
import android.text.InputType;


public class Noise implements Analysis {
    final App app;
    InvertShading inv;

    // algorithm parameters:
    final int hand_pixels[] = {};
    final long expa = 500000000;
    final long expb = 32000;
    final long expc = 50000000;
    final long expd = 5000000;
    final int run_sens[]      = {640,640,640,640};
    final long run_exposure[] = {expa,expa,expa,expa};

    int images = 1000;
    // the surveyed grid in relative units:
    //float fxl = 0.5F;
    //float fxr = 1.0F;
    //float fyl = 0.5F;
    //float fyr = 1.0F;
    float fxl = 0.0F;
    float fxr = 0.5F;
    float fyl = 0.0F;
    float fyr = 0.5F;


    // the surveyed grid will be further subdivided by:
    int   cell_numx   = 28;
    int   cell_numy   = 16;
    int   cell_step   = 89;
    int   cell_offset = 0;

    // the surveyed grid in pixels:
    int pixel_num;
    int xl,xr,yl,yr;
    int pixel_step = 1;
    int pixel_pick = 10; // number of pixels to pick randomly from each category

    // list of chosen pixels to record pixel values:
    int chosen_pixels[];
    int bad_pixels[];

    float scale;  // iso scale factor for sums
    int sum[];
    int sumsq[];
    short nonzero[];
    short pixel_data[];
    long good_hist[];
    int  max_hist = 100;

    // track run index, to cycle through iso and exposure values.
    int run_index;
    int cell_index;

    // counts:
    int requested;
    int processed;

    File outfile;

    public static Analysis newHotCells(App app) {
        Noise x = new Noise(app);
        return x;
    }

    private Noise(final App app) {
        this.app = app;
        run_index = 0;
        cell_index = 0;
        scale=1.0F;
        good_hist = new long[max_hist+1];
        int nx = app.getCamera().raw_size.getWidth();
        int ny = app.getCamera().raw_size.getHeight();
        inv = new InvertShading(nx,ny);
    }

    public void Init() {
        requested = 0;
        processed = 0;

        if (run_index >= run_sens.length){
            run_index=0;
        }

        if (run_index==0) {
            int nx = app.getCamera().raw_size.getWidth();
            int ny = app.getCamera().raw_size.getHeight();

            int   cx = cell_index % cell_numx;
            int   cy = cell_index/cell_numx;
            float dx = (fxr-fxl)/((float) cell_numx);
            float dy = (fyr-fyl)/((float) cell_numy);
            xl = (int) (nx*(fxl+dx*cx));
            xr = (int) (nx*(fxl+dx*(cx+1)));
            yl = (int) (ny*(fyl+dy*cy));
            yr = (int) (ny*(fyl+dy*(cy+1)));

            cell_index += cell_step;
            if (cell_index >= cell_numx*cell_numy){
                cell_offset++;
                if (cell_offset>=cell_step)
                    cell_offset=0;
                cell_index=cell_offset;
            }

            pixel_num = 0;
            for (int x=xl;x<xr;x+=pixel_step) {
                for (int y = yl; y < yr; y += pixel_step) {
                    pixel_num++;
                }
            }
            sum = new int[pixel_num];
            sumsq = new int[pixel_num];
            nonzero = new short[pixel_num];
            for (int i = 0; i < pixel_num; i++) {
                sum[i] = 0;
                sumsq[i] = 8*images;  // so initially, we collect "bulk" pixels
                nonzero[i] = 0;
            }
            scale=1.0F;
        }

        app.getSettings().sens     = run_sens[run_index%run_sens.length];
        app.getSettings().exposure = run_exposure[run_index%run_exposure.length];
        app.getDaq().log.append("run sensitivity:  " + app.getSettings().sens + "\n");
        app.getDaq().log.append("run exposure (mus):     " + app.getSettings().exposure/1000 + "\n");
        app.getDaq().log.append("run index:     " + run_index + "\n");
        app.getDaq().log.append("surveyed window:  " + xl + ", " + xr + ", " + yl + ", " + yr + "\n");
        app.getDaq().log.append("surveyed pixels     " + pixel_num + "\n");


        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();
        String filename = "hotcells_" + System.currentTimeMillis() + ".dat";
        outfile = new File(path, filename);

        // find lit and variant pixels
        List<Integer> bad = new ArrayList<Integer>();
        List<Integer> outlier_pixels = new ArrayList<Integer>();
        List<Integer> leaky_pixels = new ArrayList<Integer>();
        List<Integer> hivar_pixels = new ArrayList<Integer>();
        List<Integer> loose_pixels = new ArrayList<Integer>();
        List<Integer> bulk_pixels = new ArrayList<Integer>();
        List<Integer> tight_pixels = new ArrayList<Integer>();

        float fshade[] = new float[pixel_num];
        int short_index=0;
        for (int x=xl;x<xr;x+=pixel_step) {
            for (int y = yl; y < yr; y += pixel_step) {
                fshade[short_index]=1.0F/(float) inv.getfactor(x,y);
                short_index++;
            }
        }


        Random rand = new Random();
        for (int i = 0; i < pixel_num; i++) {
            float f = fshade[i];
            float mean = f*scale * ((float) sum[i]) / images;
            float var = f*f*scale*scale*((float) sumsq[i]) / images - mean * mean;

            float x   = (mean-2.0F);
            if ((mean>10)||(var>50)){
                outlier_pixels.add(i);
                bad.add(i);
            } else if ((mean>2) && (var < 3*x*x)) {
                leaky_pixels.add(i);
                bad.add(i);
            } else if (var>16){
                hivar_pixels.add(i);
                bad.add(i);
            } else if (var>11) {
                loose_pixels.add(i);
            } else if (var>6){
                bulk_pixels.add(i);
            } else {
                tight_pixels.add(i);
            }
        }
        app.getDaq().log.append("bad pixels        " + bad.size() + "\n");
        app.getDaq().log.append("- outlier pixels  " + outlier_pixels.size() + "\n");
        app.getDaq().log.append("- leaky pixels    " + leaky_pixels.size() + "\n");
        app.getDaq().log.append("- hivar pixels    " + hivar_pixels.size() + "\n");
        app.getDaq().log.append("loose pixels     " + loose_pixels.size() + "\n");
        app.getDaq().log.append("bulk pixels      " + bulk_pixels.size() + "\n");
        app.getDaq().log.append("tight pixels     " + tight_pixels.size() + "\n");

        // now that we've used last mean and var, we can reset the sums for this run:
        scale = 640/((float) app.getSettings().sens);
        for (int i = 0; i < pixel_num; i++) {
            sum[i] = 0;
            sumsq[i] = 0;
            nonzero[i] = 0;
        }
        for (int i=0;i<=max_hist;i++){
            good_hist[i]=0;
        }

        // chosen pixels are finalized at start of run index 1, and are then held constant:
        if (run_index < 2) {
            List<Integer> chosen = new ArrayList<Integer>();


            // fist add any hand chosen pixels
            for (Integer x : hand_pixels){
                chosen.add(x);
            }

            // put pixels in random order, so first N will be randomly chosen:

            Collections.shuffle(outlier_pixels);
            Collections.shuffle(leaky_pixels);
            Collections.shuffle(hivar_pixels);
            Collections.shuffle(loose_pixels);
            Collections.shuffle(bulk_pixels);
            Collections.shuffle(tight_pixels);

            for (int i = 0; i < 2*pixel_pick; i++) {
                if (i < bulk_pixels.size())
                    chosen.add(bulk_pixels.get(i));
                if (i < loose_pixels.size())
                    chosen.add(loose_pixels.get(i));
                if (i < tight_pixels.size())
                    chosen.add(tight_pixels.get(i));
            }
            for (int i = 0; i < pixel_pick; i++) {
                if (i < outlier_pixels.size())
                    chosen.add(outlier_pixels.get(i));
                if (i < leaky_pixels.size())
                    chosen.add(leaky_pixels.get(i));
                if (i < hivar_pixels.size())
                    chosen.add(hivar_pixels.get(i));
            }

            app.getDaq().log.append("chosen pixels  " + chosen.size() + "\n");
            Collections.sort(chosen);
            chosen_pixels = new int[chosen.size()];
            int i = 0;
            for (Integer x : chosen) {
                chosen_pixels[i] = x;
                i++;
            }
            Collections.sort(bad);
            bad_pixels = new int[bad.size()];
            i = 0;
            for (Integer x : bad) {
                bad_pixels[i] = x;
                i++;
            }

        }
        pixel_data = new short[chosen_pixels.length * images];

        for (int i = 0; i < pixel_num; i++) {
            sum[i] = 0;
            sumsq[i] = 0;
            nonzero[i] = 0;
        }

    }

    public Boolean Next(CaptureRequest.Builder request){
        if (requested < images){
            requested++;
            return true;
        } else {
            return false;
        }
    }
    public Boolean Done() {
        if (processed >= images) { return true; }
        else { return false; }	
    }
    public void ProcessImage(Image img) {
        Image.Plane iplane = img.getPlanes()[0];
        ByteBuffer buf = iplane.getBuffer();

        int nx = img.getWidth();
        int ny = img.getHeight();
        int ps = iplane.getPixelStride();
        Geometry geom = new Geometry(nx, ny);

        if (sum==null){
            app.log.append("null histogram encountered... app must have been recreated!");
            return;
        }

        int short_index = 0;
        int buf_index = 0;
        int next_bad  = -1;
        if (bad_pixels.length>0){
            next_bad = bad_pixels[0];
        }
        int bad_index = 1;

        short[] pixel_buf = new short[chosen_pixels.length];
        for (int y = yl; y < yr; y+=pixel_step) {
            for (int x = xl; x < xr; x+=pixel_step) {
                if (short_index >= pixel_num){
                    app.log.append("ERROR:  short_index " + short_index + " is too large\n");
                    continue;
                }
                int full_index = y * nx + x;
                short b = buf.getShort(ps * full_index);
                if (b>0) {
                    //synchronized (this) {
                    nonzero[short_index]++;
                    sum[short_index] += b;
                    sumsq[short_index] += b * b;
                    //}
                }
                if (buf_index < chosen_pixels.length) {
                    if (short_index == chosen_pixels[buf_index]) {
                        pixel_buf[buf_index] = b;
                        buf_index++;
                    }
                }
                if (short_index == next_bad) {
                    if (bad_index < bad_pixels.length) {
                        next_bad = bad_pixels[bad_index];
                        bad_index++;
                    }
                } else {
                    if (b > max_hist) {
                        b = (short) max_hist;
                    }
                    //synchronized (this) {
                    good_hist[b]++;
                    //}
                }
                short_index++;
            }
        }
        //app.getDaq().log.append("bad pixels incremented " + bad_index + "\n");
        //app.getDaq().log.append("zero count in histogram " + good_hist[0] + "\n");

        synchronized(this) {
            for (int i=0; i<chosen_pixels.length; i++) {
                pixel_data[processed*chosen_pixels.length + i] = pixel_buf[i];
            }
            processed++;
        }
    }

    public void ProcessRun() {
        int iso = app.getSettings().getSensitivity();

        try {
            FileOutputStream outstream = new FileOutputStream(outfile);
            DataOutputStream writer = new DataOutputStream(outstream);
            writer.writeInt(iso);
            writer.writeInt((int) app.getSettings().getExposure());
            writer.writeInt(images);
            writer.writeInt(app.getCamera().raw_size.getWidth());
            writer.writeInt(app.getCamera().raw_size.getHeight());
            writer.writeInt(xl);
            writer.writeInt(xr);
            writer.writeInt(yl);
            writer.writeInt(yr);
            writer.writeInt(pixel_step);
            writer.writeInt(pixel_num);
            writer.writeInt(chosen_pixels.length);
            writer.writeInt(max_hist);
            for (int x: chosen_pixels){
                writer.writeInt(x);
            }
            for (short x:  pixel_data){
                writer.writeShort(x);
            }
            for (int i = 0; i < pixel_num; i++) {
                writer.writeInt(sum[i]);
            }
            for (int i = 0; i < pixel_num; i++) {
                writer.writeInt(sumsq[i]);
            }
            for (int i = 0; i < pixel_num; i++) {
                writer.writeShort(nonzero[i]);
            }
            for (int i = 0; i <= max_hist; i++) {
                writer.writeLong(good_hist[i]);
            }

        } catch (IOException e) {
            app.getDaq().log.append("ERROR opening output file.\n");
            e.printStackTrace();
        }
        run_index++;
    }
    public String getName(int iparam) {
        if (iparam == 0) return "images";
        else return "";
    }
    public int    getType(int iparam){
        return InputType.TYPE_CLASS_NUMBER;
    }
    public String getParam(int iparam){
        if (iparam == 0) return "" + images;
        else return "";
    }
    public void   setParam(int iparam, String value) {
        if (iparam == 0) {
            if (value.length() > 8) {
                images = 99999999;
            } else {
                images = Integer.parseInt(value);
                if (images<0) images=0;
                if (images > 99999999) images = 99999999;
            }
        }
    }
}
