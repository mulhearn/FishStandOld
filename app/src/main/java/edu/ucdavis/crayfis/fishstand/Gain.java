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

public class Gain implements Analysis {

    final int total        = 1000;
    final int sample_num   = 10;
    final int sample_size  =  total/sample_num;

    int requested;
    int processed;
    int isample;

    final int h=10;
    final int w=10;

    public class Result {
        public double sum[] = new double[h * w];
        public double sum_sq[] = new double[h * w];
        public double sum_n=0;
    }

    Result results[];

    public static Analysis newGain(){
        Gain gain = new Gain();
        return gain;
    }

    private Gain() {
        results = new Result[sample_num];
        for (int i=0; i<sample_num; i++) {
            results[i] = new Result();
        }
    }

    public void Init(){
    	requested=0;
	    processed=0;
        isample=0;

        for (int i=0; i<sample_num; i++) {
            results[i].sum_n = 0;
            for (int j=0; j<h*w; j++){
                results[i].sum[j]=0;
                results[i].sum_sq[j]=0;
            }
        }
    }

    public Boolean Next(CaptureRequest.Builder request){
        if (requested < total){
            requested = requested + 1;
            return true;
        } else {
            return false;
        }
    }
    public Boolean Done() {
        if (processed >= total) { return true; }
        else { return false; }
    }

    public void ProcessImage(Image img){
        Image.Plane iplane = img.getPlanes()[0];
        ByteBuffer buf = iplane.getBuffer();

        isample = processed / sample_size;
        if (isample >= sample_num){
            // we have some extra events... go with it:
            isample = sample_size-1;
        }

        int off_w = (img.getWidth() - w) >> 1;
        int off_h = (img.getHeight() - h) >> 1;
        int pw = iplane.getPixelStride();
        int rw = iplane.getRowStride();

    	results[isample].sum_n++;
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int index = rw * (off_h + i) + pw * (off_w + j);
                char b = buf.getChar(index);
                BkgWorker.getBkgWorker().daq.pixels.increment(b);
                int iflat = i*w+j;
                double x = (double) b;
                results[isample].sum[iflat] += x;
                results[isample].sum_sq[iflat] += x*x;
            }
        }
        BkgWorker.getBkgWorker().daq.summary = "";
        BkgWorker.getBkgWorker().daq.summary += results[isample].sum[0] + ", " + results[isample].sum_sq[0] + ", " + results[isample].sum_n + "\n";
        BkgWorker.getBkgWorker().daq.summary += results[isample].sum[1] + ", " + results[isample].sum_sq[1] + ", " + results[isample].sum_n + "\n";
        BkgWorker.getBkgWorker().daq.update = true;
        processed = processed+1;
    }

    public void ProcessRun(){
        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();

        String filename = "gain_" + System.currentTimeMillis() + ".txt";
        File outfile = new File(path, filename);
        try {
            FileWriter writer = new FileWriter(outfile);
            for (int i=0; i<sample_num; i++){
                for (int j=0;j<h*w;j++) {
                    writer.append("" + i + ", " + j + ", " + results[i].sum[j] + ", " + results[i].sum_sq[j] + ", " + results[i].sum_n + "\n");
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            BkgWorker.getBkgWorker().daq.summary += "ERROR opening txt file in Gain analysis.";
            e.printStackTrace();
        }
    }
}
