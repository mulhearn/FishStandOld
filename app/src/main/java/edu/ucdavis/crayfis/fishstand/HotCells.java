package edu.ucdavis.crayfis.fishstand;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;
import android.text.InputType;


public class HotCells implements Analysis {
    final App app;
    // camera properties:
    Geometry geom;
    int pixel_num;

    // algorithm parameters:
    final int images = 1000;
    int step = 10;
    int pixel_pick = 10; // number of pixels to pick randomly from each category

    // list of chosen pixels to record pixel values:
    int chosen_pixels[];

    int sum[];
    int sumsq[];
    float mean[];
    float var[];
    short pixel_data[];

    // counts:
    int requested;
    int processed;

    File outfile;

    public static Analysis newHotCells(App app) {
        HotCells x = new HotCells(app);
        return x;
    }

    private void clear_histograms() {
        for (int i = 0; i < pixel_num; i++) {
            sum[i] = 0;
            sumsq[i] = 0;
        }
    }

    private HotCells(final App app) {
        this.app = app;
        int nx = app.getCamera().raw_size.getWidth();
        int ny = app.getCamera().raw_size.getHeight();
        pixel_num = nx * ny;
        if (step > 1) {
            int nxr = nx / step + 1;  // need a one here because < becomes <= for some cases in for loops
            int nyr = ny / step + 1;
            pixel_num = nxr * nyr;
        }
        geom = new Geometry(nx, ny);
    }

    public void Init() {
        // TODO:  provide a proto-image to Init from DaqWorker.
        requested = 0;
        processed = 0;
        app.log.append("Geometry self test output:  ");
        app.log.append(Geometry.SelfTest());

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();
        String filename = "hotcells_" + System.currentTimeMillis() + ".dat";
        outfile = new File(path, filename);
        if (sum == null) {
            sum = new int[pixel_num];
        }
        if (sumsq == null) {
            sumsq = new int[pixel_num];
        }
        if (mean == null) {
            mean = new float[pixel_num];
            for (int i = 0; i < pixel_num; i++) {
                mean[i] = 0;
            }
        }
        if (var == null) {
            var = new float[pixel_num];
            for (int i = 0; i < pixel_num; i++) {
                var[i] = 0;
            }
        }
        clear_histograms();

        // find lit and variant pixels
        List<Integer> lit_pixels = new ArrayList<Integer>();
        List<Integer> var_pixels = new ArrayList<Integer>();
        List<Integer> nrm_pixels = new ArrayList<Integer>();
        List<Integer> chosen     = new ArrayList<Integer>();

        for (int i = 0; i < pixel_num; i++) {
            if (var[i] > 50 + 6 * mean[i]) {
                var_pixels.add(i);
            } else if (mean[i] > 10) {
                lit_pixels.add(i);
            } else if ((mean[i] < 5) && (var[i]<20)){
                nrm_pixels.add(i);
            }
        }
        app.getDaq().log.append("lit pixels     " + lit_pixels.size() + "\n");
        app.getDaq().log.append("variant pixels " + var_pixels.size() + "\n");
        app.getDaq().log.append("normal pixels  " + nrm_pixels.size() + "\n");

        // put in random order, so first N will be randomly chosen:
        Collections.shuffle(lit_pixels);
        Collections.shuffle(var_pixels);
        Collections.shuffle(nrm_pixels);
        for (int i = 0; i < pixel_pick; i++) {
            if (i<nrm_pixels.size())
                chosen.add(nrm_pixels.get(i));
            if (i<lit_pixels.size())
                chosen.add(lit_pixels.get(i));
            if (i<var_pixels.size())
                chosen.add(var_pixels.get(i));
        }
        app.getDaq().log.append("chosen pixels  " + chosen.size() + "\n");

        Collections.sort(chosen);
        chosen_pixels = new int[chosen.size()];
        int i = 0;
        for (Integer x: chosen) {
            chosen_pixels[i] = x;
            i++;
        }
        pixel_data = new short[chosen_pixels.length*images];
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
        short[] pixel_buf = new short[chosen_pixels.length];
        for (int x = 0; x < nx; x+=step) {
            for (int y = 0; y < ny; y+=step) {
                if (short_index >= pixel_num){
                    app.log.append("ERROR:  short_index " + short_index + " is too large\n");
                    continue;
                }
                int full_index = y * nx + x;
                short b = buf.getShort(ps * full_index);
                sum[short_index] += b;
                sumsq[short_index] += b*b;
                if (buf_index < chosen_pixels.length) {
                    if (short_index == chosen_pixels[buf_index]) {
                        pixel_buf[buf_index] = b;
                        buf_index++;
                    }
                }
                short_index++;
            }
        }
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
            writer.writeInt(geom.num_x);
            writer.writeInt(geom.num_y);
            writer.writeInt(step);
            writer.writeInt(pixel_num);
            writer.writeInt(chosen_pixels.length);
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
                writer.writeFloat(mean[i]);
            }
            for (int i = 0; i < pixel_num; i++) {
                writer.writeFloat(var[i]);
            }
        } catch (IOException e) {
            app.getDaq().log.append("ERROR opening output file.\n");
            e.printStackTrace();
        }
        // calculate mean and variance for use in next run:
        for (int i = 0; i < pixel_num; i++) {
            mean[i] = ((float) sum[i]) / images;
            var[i] = ((float) sumsq[i]) / images - mean[i] * mean[i];
        }

    }
    public String getName(int iparam){return "";}
    public int    getType(int iparam){return InputType.TYPE_CLASS_NUMBER;} // or TYPE_CLASS_TEXT
    public String getParam(int iparam){ return ""; }
    public void   setParam(int iparam, String value) {}
}
