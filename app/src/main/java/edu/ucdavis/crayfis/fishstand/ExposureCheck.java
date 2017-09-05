package edu.ucdavis.crayfis.fishstand;

import java.nio.ByteBuffer;

import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.media.Image;
import android.media.ImageReader;

import static android.content.ContentValues.TAG;
import edu.ucdavis.crayfis.fishstand.App;

public class ExposureCheck {
    App app;
    public ExposureCheck(App app){this.app = app;}

    public final int max_dn = 1024; // find in config?
    public final int nbins = 50;
    public int[] hist = new int[nbins];

    public void ButtonPressed() {
        Runnable r = new Runnable() {
            public void run() {
                Run();
            }
        };
        app.getBkgHandler().post(r);
    }

    ImageReader.OnImageAvailableListener exposureImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            app.short_toast("Image for Exposure Check.");
            Image img = reader.acquireNextImage();
            Image.Plane p = img.getPlanes()[0];

            int ps = p.getPixelStride();
            int rs = p.getRowStride();
            int h  = img.getHeight();
            int w  = img.getWidth();

            ByteBuffer buf = p.getBuffer();
            for (int i=0; i < h*w; i+=100){
                char c = buf.getChar(2*i);
                int ibin = c * nbins / max_dn;
                if (ibin>=nbins){ibin=nbins-1;}
                if (ibin<0) ibin=0;
                hist[ibin]++;
            }
            img.close();
            app.getMessage().updateExposureResults();
        }
    };

    void Run(){
        app.getCamera().ireader.setOnImageAvailableListener(exposureImageListener, app.getBkgHandler());
        hist = new int[nbins];
        try {
            final CaptureRequest.Builder captureBuilder = app.getCamera().cdevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            captureBuilder.addTarget(app.getCamera().ireader.getSurface());
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, app.getSettings().getExposure());
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, app.getSettings().getSensitivity());
            captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, app.getCamera().max_frame);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            float fl = (float) 0.0;
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, fl);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);

            Image img = app.getCamera().ireader.acquireLatestImage();
            if (img != null){
                app.short_toast("discarding unexpected image.\n");
                img.close();
            }

            app.getCamera().csession.capture(captureBuilder.build(), doNothingCaptureListener, app.getBkgHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    ImageReader.OnImageAvailableListener ExposureImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            app.short_toast("Test Image Available");
        }
    };

    final CameraCaptureSession.CaptureCallback doNothingCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (result != null) {
                app.short_toast("Exposure Capture Complete");
            }
        }
    };

}
