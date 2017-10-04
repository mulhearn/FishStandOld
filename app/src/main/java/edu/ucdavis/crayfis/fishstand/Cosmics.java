package edu.ucdavis.crayfis.fishstand;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;
import android.text.InputType;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


public class Cosmics implements Analysis {
    final App app;
    InvertShading inv;
    RegionLock lock;
    Geometry geom;
    Random rand;
    int images = 1000;

    // the surveyed grid in relative units:
    //float fxl = 0.1F;
    //float fxr = 0.9F;
    //float fyl = 0.1F;
    //float fyr = 0.9F;
    float fxl = 0.25F;
    float fxr = 0.75F;
    float fyl = 0.25F;
    float fyr = 0.75F;

    // step size in x and y when traversing grid:
    int pixel_step = 1;

    // pixel quality monitoring sums:
    float scale;  // iso scale factor for sums
    int sum[];
    int sumsq[];
    short nonzero[];

    // list of bad pixels:
    int bad_pixels[];

    // maximum number of regions:
    // (for simplicity, the number of regions is the same as the monitoring period)
    int num_regions = 20;
    // starting index of bad channel list for each region:
    int bad_start[];
    // has a full calibration been obtained yet?
    Boolean calibrated=false;
    // phase in the monitoring cycle
    int mon_phase=0;

    // counts:
    int requested;
    int processed;

    long good_hist[][];
    short hist_max = 100;
    int num_histx;

    File path;
    String outfilename;
    String trigfilename;

    // trigger menu:
    int num_zb = 20;
    int thresh[]   = {30, 25, 20,  15};
    int prescale[] = { 0, 10, 100, 1000};

    public static Analysis newCosmics(App app) {
        Cosmics x = new Cosmics(app);
        return x;
    }

    private Cosmics(final App app) {
        this.app = app;
        scale=1.0F;
        int nx = app.getCamera().raw_size.getWidth();
        int ny = app.getCamera().raw_size.getHeight();
        geom = new Geometry(nx,ny);
        inv = new InvertShading(nx,ny);

        geom.SetMargins(fxl,fxr,fyl,fyr);
        geom.SetPixelStep(pixel_step);
        geom.SetNumRegions(num_regions);
        num_regions = geom.num_regions;
        lock = new RegionLock(num_regions);
        num_histx = num_regions;
        good_hist = new long[num_histx*num_regions][hist_max+1];
        rand = new Random();
    }

    public void Init() {
        requested = 0;
        processed = 0;

        if (mon_phase >= num_regions){
            mon_phase = 0;
        }

        app.getDaq().log.append("initializing...\n");



        // run the Geometry class self test:
        //String s = Geometry.SelfTest();
        //app.log.append(s);

        if (!calibrated) {
            app.getDaq().log.append("allocating memory for sums... please wait.\n");
            sum = new int[geom.num_pixel];
            sumsq = new int[geom.num_pixel];
            nonzero = new short[geom.num_pixel];
            for (int i = 0; i < geom.num_pixel; i++) {
                sumsq[i] = 8 * images;  // so initially, we collect "bulk" pixels
            }
            scale = 640 / ((float) app.getSettings().sens);
        }

        path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();
        long tstamp = System.currentTimeMillis();
        outfilename = "cosmics_" + tstamp + ".dat";
        trigfilename = "trig_" + tstamp + ".dat";

        app.getDaq().log.append("updating bad pixel list... please wait.\n");
        List<Integer> bad = new ArrayList<Integer>();

        float fshade[] = new float[geom.num_pixel];
        int short_index = 0;
        for (int x = geom.xl; x < geom.xr; x += pixel_step) {
            for (int y = geom.yl; y < geom.yr; y += pixel_step) {
                fshade[short_index] = 1.0F/(float) inv.getfactor(x, y);
                short_index++;
            }
        }
        for (int i = 0; i < geom.num_pixel; i++) {
            float f = fshade[i];
            float mean = f * scale * ((float) sum[i]) / images;
            float var = f * f * scale * scale * ((float) sumsq[i]) / images - mean * mean;

            float x = (mean - 2.0F);
            if ((mean > 10) || (var > 50)) {
                bad.add(i);
            } else if ((mean > 2) && (var < 3 * x * x)) {
                bad.add(i);
            } else if (var > 16) {
                bad.add(i);
                //} else if (var>11) {
                //    bad.add(i);
                //}
            }
        }

        // now that we've used last mean and var, we can reset the sums for this run:
        // if not calibrated, we'll be monitoring every pixel:
        if (!calibrated) {
            for (int ipixel = 0; ipixel < geom.num_pixel; ipixel++) {
                sum[ipixel] = 0;
                sumsq[ipixel] = 0;
                nonzero[ipixel] = 0;
            }
        }
        // once calibrated, we only update one region per run:
        for (int ipixel = geom.GetRegionStart(mon_phase); ipixel < geom.GetRegionEnd(mon_phase); ipixel++) {
            sum[ipixel] = 0;
            sumsq[ipixel] = 0;
            nonzero[ipixel] = 0;
        }

        // also clear the good pixel histogram for this run:
        for (int i=0; i<num_histx*num_regions; i++){
            for (int j=0; j<=hist_max; j++){
                good_hist[i][j]=0;
            }

        }
        
        Collections.sort(bad);
        bad_pixels = new int[bad.size()];
        int index = 0;
        for (Integer x : bad) {
            bad_pixels[index] = x;
            index++;
        }
        bad_start = new int[geom.num_regions];
        for (int i=0;i<num_regions;i++){
            int region_start = geom.region_start[i];
            int ibad = 0;
            while ((ibad<bad_pixels.length)&&(bad_pixels[ibad]<region_start)){
                ibad++;
            }
            bad_start[i] = ibad;
        }
        app.getDaq().log.append("number of bad pixels:     " + bad_pixels.length + "\n");

        app.log.append("surveyed window:  " + geom.xl + ", " + geom.xr + ", " + geom.yl + ", " + geom.yr + "\n");
        app.log.append("surveyed pixels     " + geom.num_pixel + "\n");
        String s = "";
        s += "num_regions = " + geom.num_regions + "\n";
        for (int i=0; i<geom.num_regions; i++){
            s += i + ": yl: " + geom.region_yl[i] + ": yr: " + geom.region_yr[i] + " start: " + geom.region_start[i] + "\n";
        }
        app.log.append(s);

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

    private void AppendWindow(List<Integer> trig_buf, ByteBuffer buf, int nx, int ps, int trig, int ix, int iy){
        //app.log.append("adding triger at " + ix +", " + iy + "\n");

        int max = 28000;
        if (trig_buf.size() >= max){
            return;
        }
        final int w= 2;
        trig_buf.add(trig);
        trig_buf.add(ix);
        trig_buf.add(iy);
        for (int y=iy-w; y<=iy+w; y+=1){
            for (int x=ix-w; x<=ix+w; x+=1){
                int full_index = y * nx + x;
                short b = buf.getShort(ps * full_index);
                trig_buf.add((int) b);
            }
        }
    }

    public void ProcessImage(Image img) {
        Image.Plane iplane = img.getPlanes()[0];
        ByteBuffer buf = iplane.getBuffer();

        int nx = img.getWidth();
        int ny = img.getHeight();
        int ps = iplane.getPixelStride();

        Boolean todo[] = lock.newToDoList();
        List<Integer> trig_buf = new ArrayList<Integer>();
        int ntrig[] = new int[thresh.length];
        do {
            int region = lock.lockRegion(todo);
            while (region < 0) {
                // no unfinished and available region, so wait a spell:
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                region = lock.lockRegion(todo);
            }
            // I have exclusive access to region <region>

            // first perform calibration if required:
            if ((!calibrated) || (region == mon_phase)) {
                int short_index = geom.region_start[region];
                for (int y = geom.region_yl[region]; y < geom.region_yr[region]; y += geom.pixel_step) {
                    for (int x = geom.xl; x < geom.xr; x += geom.pixel_step) {
                        int full_index = y * nx + x;
                        if (short_index >= geom.num_pixel) {
                            app.log.append("ERROR:  short_index " + short_index + " is too large\n");
                            continue;
                        }
                        short b = buf.getShort(ps * full_index);
                        nonzero[short_index]++;
                        if (b > 0) {
                            sum[short_index] += b;
                            sumsq[short_index] += b * b;
                        }
                        short_index++;
                    }
                }
            }
            // next perform trigger analysis on good channels:
            int short_index = geom.region_start[region];
            int bad_index = bad_start[region];
            int next_bad = -1;
            if (bad_index < bad_pixels.length)
                next_bad = bad_pixels[bad_index];
            for (int y = geom.region_yl[region]; y < geom.region_yr[region]; y += geom.pixel_step) {
                for (int x = geom.xl; x < geom.xr; x += geom.pixel_step) {
                    if (short_index == next_bad) {
                        bad_index++;
                        if (bad_index < bad_pixels.length) {
                            next_bad = bad_pixels[bad_index];
                        }
                    } else {
                        float f = (float) inv.getfactor(x,y);
                        int full_index = y * nx + x;
                        short b = buf.getShort(ps * full_index);

                        if (calibrated) {
                            // apply trigger requirements and prescales:
                            for (int itrig = 0; itrig < thresh.length; itrig++) {
                                if (b > thresh[itrig] * f) {
                                    if (prescale[itrig] == 0) {
                                        AppendWindow(trig_buf, buf, nx, ps, itrig + 1, x, y);
                                        ntrig[itrig]++;
                                        break;
                                    } else {
                                        double throwx = rand.nextDouble() * prescale[itrig];
                                        if (throwx < 1.0) {
                                            AppendWindow(trig_buf, buf, nx, ps, itrig + 1, x, y);
                                            ntrig[itrig]++;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        // fill regional good channel histogram
                        b = (short) (b / f);
                        int ixbin = (x-geom.xl)*num_histx/geom.xw;
                        if (b>100) {
                            b = 100;
                        }
                        good_hist[region*num_histx+ixbin][b] += 1;
                    }
                    short_index++;
                }
            }
            lock.releaseRegion(region);
        } while(lock.stillWorking(todo));

        for (int i=0;i<num_zb; i++){
            int x = geom.xl + (int) (geom.xw*rand.nextDouble());
            int y = geom.yl + (int) (geom.yh*rand.nextDouble());
            AppendWindow(trig_buf, buf, nx, ps, 0, x, y);
        }

        String s = "image processing complete:  \n";
        for (int i=0; i<ntrig.length; i++) {
            s += "thresh " + thresh[i] + " prescale " + prescale[i] + " ntrig:  " + ntrig[i] + "\n";
        }
        app.getDaq().log.append(s);
        synchronized(this) {
            try {

                FileOutputStream outstream = new FileOutputStream(new File(path,trigfilename),true);
                DataOutputStream writer = new DataOutputStream(outstream);
                final int HEADER_SIZE = 3;
                final int VERSION = 1;
                writer.writeLong(img.getTimestamp());
                writer.writeInt(HEADER_SIZE);
                writer.writeInt(VERSION);
                // additional header items
                writer.writeInt(ntrig.length);
                writer.writeInt(num_zb);
                writer.writeInt(trig_buf.size());
                for (int x : ntrig) {
                    writer.writeInt(x);
                }
                for (int x : trig_buf) {
                    writer.writeInt(x);
                }
                writer.flush();
                writer.close();
            } catch (IOException e) {
                app.getDaq().log.append("ERROR opening output file.\n");
                e.printStackTrace();
            }
            processed++;
        }
    }

    public void ProcessRun() {
        int region_start = geom.GetRegionStart(mon_phase);
        int region_end   = geom.GetRegionEnd(mon_phase);
        //int region_start = 0;
        //int region_end = geom.num_pixel;
        calibrated = true;
        try {
            FileOutputStream outstream = new FileOutputStream(new File(path,outfilename));
            DataOutputStream writer = new DataOutputStream(outstream);
            final int HEADER_SIZE = 21;
            final int VERSION = 2;
            writer.writeInt(HEADER_SIZE);
            writer.writeInt(VERSION);
            // additional header items
            writer.writeInt((int) app.getSettings().getSensitivity());
            writer.writeInt((int) app.getSettings().getExposure());
            writer.writeInt(images);
            writer.writeInt(processed);
            writer.writeInt(app.getCamera().raw_size.getWidth());
            writer.writeInt(app.getCamera().raw_size.getHeight());
            writer.writeInt(geom.xl);
            writer.writeInt(geom.xr);
            writer.writeInt(geom.yl);
            writer.writeInt(geom.yr);
            writer.writeInt(pixel_step);
            writer.writeInt(geom.num_pixel);
            writer.writeInt(bad_pixels.length);
            writer.writeInt(mon_phase);
            writer.writeInt(region_start);
            writer.writeInt(region_end);
            writer.writeInt(num_regions);
            writer.writeInt(num_histx);
            writer.writeInt(hist_max);
            writer.writeInt(num_zb);
            writer.writeInt(thresh.length);
            for (int x: thresh){
                writer.writeInt(x);
            }
            for (int x: prescale){
                writer.writeInt(x);
            }
            for (int x: bad_pixels){
                writer.writeInt(x);
            }
            for (int i = region_start; i < region_end; i++) {
                writer.writeInt(sum[i]);
            }
            for (int i = region_start; i < region_end; i++) {
                writer.writeInt(sumsq[i]);
            }
            for (int i = region_start; i < region_end; i++) {
                writer.writeShort(nonzero[i]);
            }
            for (long g[]: good_hist) {
                for (long gx : g) {
                    writer.writeLong(gx);
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            app.getDaq().log.append("ERROR opening output file.\n");
            e.printStackTrace();
        }
        mon_phase++;
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
