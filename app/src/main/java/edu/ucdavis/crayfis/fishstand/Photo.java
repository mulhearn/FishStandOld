package edu.ucdavis.crayfis.fishstand;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Environment;
import android.text.InputType;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Photo implements Analysis {
    final App app;
    int num = 1;
    String tag = "";
    int requested;
    int processed;

    public static Analysis newPhoto(App app){
        Photo photo = new Photo(app);
        return photo;
    }

    private Photo(App app) {
        this.app = app;
        requested=0;
        processed=0;
    }

    public void Init(){requested=0; processed=0; }

    public long DelayStart(){return 0;}

    public Boolean Next(CaptureRequest.Builder request){
        if (requested < 1){
            requested = requested + 1;
            return true;
        } else {
            return false;
        }
    }
    public Boolean Done() {
        if (processed > 0) { return true; }
        else { return false; }
    }

    public void ProcessImage(Image img){
        Image.Plane iplane = img.getPlanes()[0];
        ByteBuffer buf = iplane.getBuffer();

        int w = (int) (0.2 *  ((float) img.getWidth()));
        int h = (int) (0.2 *  ((float) img.getHeight()));
        //make a square shaped image:
        if (w>h) h=w;
        else     w=h;

        int off_w = (img.getWidth() - w) >> 1;
        int off_h = (img.getHeight() - h) >> 1;
        int pw = iplane.getPixelStride();
        int rw = iplane.getRowStride();

        app.getDaq().bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Bitmap bm = app.getDaq().bitmap;

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int index = rw*(off_h + i) + pw*(off_w + j);
                char b = buf.getChar(index);
                app.getDaq().pixels.increment(b);

                if (b > 0xff) b = 0xff;
                byte x = (byte) b;
                //int x = b & 0xFF;
                //if (b > 0xFF) x = 0xFF;
                //x = ~x;
                bm.setPixel(j, i, Color.argb(0xff, x, x, x));
            }
        }

        //File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        //        "FishStand");
        //path.mkdirs();

        // quick delete:
        //if (path.isDirectory()) {
        //    String[] files = path.list();
        //    for (String file : files){
        //        new File(path, file).delete();
        //    }
        //}

        //String filename = "image_" + System.currentTimeMillis() + ".jpg";
        //File outfile = new File(path, filename);
        //BufferedOutputStream bos = null;
        //try {
        //    bos = new BufferedOutputStream(new FileOutputStream(outfile));
        //} catch (FileNotFoundException e) {
        //    e.printStackTrace();
        //    return;
        //}
        //bm.compress(Bitmap.CompressFormat.JPEG, 50, bos);

        processed = processed+1;
    }

    public void ProcessImageOld(Image img){

        ByteBuffer buf = img.getPlanes()[0].getBuffer();

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "FishStand");
        path.mkdirs();

        // quick delete:
        //if (path.isDirectory()) {
        //    String[] files = path.list();
        //    for (String file : files){
        //        new File(path, file).delete();
        //    }
        //}

        String baseName = "image_" + System.currentTimeMillis();
        File bmpfile = new File(path, baseName + ".bmp");
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(bmpfile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        try {
            // our device (for now hard coded in header below:)
            //>>> hex(3000)
            //'0xbb8'
            //>>> hex(5328)
            //'0x14d0'

            char[] header = {0x42,0x4d,0x4c,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x1a,0x00,0x00,0x00,0x0c,0x00,
                    0x00,0x00,0xd0,0x14,0xb8,0x0b,0x01,0x00,0x18,0x00};
            char[] dummy  = {0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00};
            char[] trailer = {0x00, 0x00};
            for (char b : header) {
                bos.write((byte) b);
            }
            for (int row = 0; row < 3000; row++) {
                for (int col = 0; col < 5328; col++) {
                    char b = buf.getChar();
                    int x = b&0xFF;
                    if (b > 0xFF) x = 0xFF;

                    // for now do BW, but here we could select based on color filter pattern...
                    bos.write((byte) x);
                    bos.write((byte) x);
                    bos.write((byte) x);

                }
            }
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        processed = processed+1;
    }

    public void ProcessRun(){

    }

    public String getName(int iparam) {
        if (iparam == 0) return "num";
        if (iparam == 1) return "tag";

        else return "";
    }
    public int    getType(int iparam){
        if (iparam == 1) return InputType.TYPE_CLASS_TEXT;
        return InputType.TYPE_CLASS_NUMBER;
    }
    public String getParam(int iparam){
        if (iparam == 0) return "" + num;
        if (iparam == 1) return "" + tag;
        else return "";
    }
    public void   setParam(int iparam, String value) {
        if (iparam == 0) {
            if (value.length() > 8) {
                num = 99999999;
            } else {
                num = Integer.parseInt(value);
                if (num<0) num=0;
                if (num > 99999999) num = 99999999;
            }
        }
        if (iparam == 1) {
            tag = value;
        }
    }
}
