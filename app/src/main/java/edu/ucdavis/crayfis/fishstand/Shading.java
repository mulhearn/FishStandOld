package edu.ucdavis.crayfis.fishstand;

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


public class Shading implements Analysis {
    final App app;
    InvertShading inv;

    // algorithm parameters:
    int   images = 1000;
    int   pixel_step = 1;
    //int   cell_numx = 28;
    //int   cell_numy = 16;
    int   cell_numx = 56;
    int   cell_numy = 32;
    short max_hist  = 100;
    long raw[][];
    long scaled[][];

    // counts:
    int requested;
    int processed;

    File outfile;

    public static Analysis newShading(App app) {
        Shading x = new Shading(app);
        return x;
    }

    private Shading(final App app) {
        this.app = app;
        raw = new long[cell_numx*cell_numy][max_hist+1];
        scaled = new long[cell_numx*cell_numy][max_hist+1];
        int nx = app.getCamera().raw_size.getWidth();
        int ny = app.getCamera().raw_size.getHeight();
        inv = new InvertShading(nx,ny);
    }

    public void Init() {
        requested = 0;
        processed = 0;

        app.getDaq().log.append("run sensitivity:  " + app.getSettings().sens + "\n");
        app.getDaq().log.append("run exposure (mus):     " + app.getSettings().exposure/1000 + "\n");

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();
        String filename = "shading_" + System.currentTimeMillis() + ".dat";
        outfile = new File(path, filename);

        for (int icell=0; icell<cell_numx*cell_numy; icell++){
            for (int ipixval=0; ipixval<max_hist+1; ipixval++) {
                raw[icell][ipixval] = 0;
                scaled[icell][ipixval] = 0;
            }
        }

        app.log.append("Lens Correction Inversion");
        int nx = app.getCamera().raw_size.getWidth();
        int ny = app.getCamera().raw_size.getHeight();
        for (int iy = 0; iy < ny; iy += 100) {
            for (int ix=0;ix<nx; ix+=100) {
                app.log.append("" + ix + ", " + iy + ": " + inv.getfactor(ix, iy) + "\n");
            }
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

        for (int y = 0; y < ny; y+=pixel_step) {
            for (int x = 0; x < nx; x+=pixel_step) {
                int cy = (y*cell_numy)/ny;
                int cx = (x*cell_numx)/nx;
                int cell_index = cell_numx*cy + cx;
                int full_index = y * nx + x;
                short b = buf.getShort(ps * full_index);
                if (b > max_hist) {
                    b = max_hist;
                }
                // this may lose data, but too expensive...  use regional locks?
                //synchronized (this) {
                raw[cell_index][b]++;
                short s = (short) (b/inv.getfactor(x,y));
                scaled[cell_index][s]++;
                //}
            }
        }
        synchronized(this) {
            processed++;
        }
    }

    public void ProcessRun() {
        try {
            FileOutputStream outstream = new FileOutputStream(outfile);
            DataOutputStream writer = new DataOutputStream(outstream);
            final int HEADER_SIZE = 10;
            final int VERSION = 1;
            writer.writeInt(HEADER_SIZE);
            writer.writeInt(VERSION);
            // begin custom header
            writer.writeInt(images);
            writer.writeInt(processed);
            writer.writeInt(pixel_step);
            writer.writeInt(app.getSettings().getSensitivity());
            writer.writeInt((int) app.getSettings().getExposure());
            writer.writeInt(app.getCamera().raw_size.getWidth());
            writer.writeInt(app.getCamera().raw_size.getHeight());
            writer.writeInt(cell_numx);
            writer.writeInt(cell_numy);
            writer.writeInt(max_hist);
            for (long h[]: raw) {
                for (long x : h) {
                    writer.writeLong(x);
                }
            }
            for (long h[]: scaled) {
                for (long x : h) {
                    writer.writeLong(x);
                }
            }
        } catch (IOException e) {
            app.getDaq().log.append("ERROR opening output file.\n");
            e.printStackTrace();
        }
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
